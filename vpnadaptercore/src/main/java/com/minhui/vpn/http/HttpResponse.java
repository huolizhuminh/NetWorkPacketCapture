package com.minhui.vpn.http;

import android.text.TextUtils;


import com.minhui.vpn.utils.AppDebug;
import com.minhui.vpn.utils.Compressor;
import com.minhui.vpn.utils.CompressorFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Set;

/**
 * Created by zengzheying on 15/12/31.
 */
public class HttpResponse {

	public static final String KEY_CONTENT_TYPE = "Content-Type";
	public static final String KEY_CONTENT_LENGTH = "Content-Length";
	public static final String KEY_TRANSFER_ENCODING = "Transfer-Encoding";
	public static final String KEY_CONTENT_ENCODING = "Content-Encoding";
	byte[] mData;
	boolean isCompleted; //已经完整地接收了Http包
	boolean isBlockInfo = false;
	boolean isParsedHeader = false;
	int limit = 40960;
	int position = -1;
	private String mStateLine = "";
	private int mStateCode;
	private String mContentType;
	private String mContentEncoding;
	private String mTransferEncoding;
	private String mCharset = "utf-8"; //默认body编码utf-8
	private long mContentLength;
	private String mBody = "";
	private HashMap<String, String> headers = new HashMap<>();
	private int waitForParseStartIndex = 0; //待解析的位置
	private int bodyStart = 0; //body起始位
	private ByteArrayOutputStream mBodyByteArray;

	public HttpResponse(boolean isBlockInfo) {
		mData = new byte[limit];
		this.isBlockInfo = isBlockInfo;
	}

	public void write(byte b) {
		if (position + 1 < limit) {
			mData[++position] = b;
		} else {
			byte[] newData = new byte[limit * 2];
			System.arraycopy(mData, 0, newData, 0, position + 1);
			mData = newData;
			limit *= 2;
			write(b);
		}
	}

	public void write(ByteBuffer buffer) {
		if (buffer != null) {
			int oldPosition = buffer.position();
			while (buffer.hasRemaining()) {
				write(buffer.get());
			}
			buffer.position(oldPosition);

			parse();
		}

	}

	private void parse() {
		if (!isParsedHeader) { //还没解析头部
			if (waitForParseStartIndex <= position) {
				parseHttpHeader(waitForParseStartIndex, (position + 1) - waitForParseStartIndex);
			}
		}

		if (isParsedHeader && !isCompleted) { //解析了头部，不能和上面的if 合并成 if...else...结构，因为想处理了头部，若条件
			//合适，可以一并处理报文
			if (isParsedHeader && shouldHaveNoBody()) { //不允许带有实体的报文类型
				isCompleted = true;
			}
			if (!isCompleted) {
				if (!TextUtils.isEmpty(mTransferEncoding)
						&& "chunked".equalsIgnoreCase(mTransferEncoding.trim())) {
					// 分块传输的报文
					while (waitForParseStartIndex + 2 <= position) {
						String content = new String(mData, waitForParseStartIndex, position - waitForParseStartIndex);
						String divider = "\r\n";
						if (content.contains(divider)) {
							String lengthString = content.substring(0, content.indexOf(divider));
							int length = Integer.valueOf(lengthString.trim(), 16);
							if (length != 0 && (position - waitForParseStartIndex >=
									(2 + 2 * divider.getBytes().length + length))) { //块长度字段大小 + 2个\r\n长度 + 块长度
								//块完整
								if (mBodyByteArray == null) {
									mBodyByteArray = new ByteArrayOutputStream();
								}
								waitForParseStartIndex += lengthString.getBytes().length + divider.getBytes().length;
								mBodyByteArray.write(mData, waitForParseStartIndex, length);
								waitForParseStartIndex += length + divider.getBytes().length;
							} else if (length == 0) { //分块传输接收完毕
								isCompleted = true;
								parseHttpBody();
								break;
							} else { //块不完整
								break;
							}
						} else {
							break;
						}
					}
				} else {
					int bodyLengthReceived = position - bodyStart + 1;
					if (bodyLengthReceived == mContentLength) {
						isCompleted = true;
						if (mBodyByteArray == null) {
							mBodyByteArray = new ByteArrayOutputStream();
						}
						mBodyByteArray.write(mData, waitForParseStartIndex, bodyLengthReceived);
						parseHttpBody();
					}
				}
			}
		}
	}


	private void parseHttpHeader(int start, int count) {
		String content = new String(mData, start, count);
		String divider = "\r\n\r\n";
		if (content.contains(divider)) { //头部完整
			isParsedHeader = true;
			String[] headers = content.split(divider)[0].split("\r\n");
			boolean parseStateLine = parseStateLine(headers[0]);
			parseHeader(headers, parseStateLine ? 1 : 0);
			int headerLength = content.substring(0, content.indexOf(divider)).getBytes().length;
			waitForParseStartIndex = headerLength + divider.getBytes().length;
			bodyStart = waitForParseStartIndex;
			isCompleted = (TextUtils.isEmpty(mTransferEncoding)
					|| !"chunked".equalsIgnoreCase(mTransferEncoding.trim()))
					&& mContentLength == 0;
		}
	}

	private void parseHttpBody() {
		if (mBodyByteArray != null && isHtml()) {
			if (!TextUtils.isEmpty(mContentEncoding)) { //采用压缩
				Compressor compressor = CompressorFactory.getCompressor(mContentEncoding);
				if (compressor != null) {
					try {
						byte[] source = compressor.uncompress(mBodyByteArray.toByteArray());
						if (source != null) {
							mBody = new String(source, mCharset);
						}
					} catch (Exception ex) {
						if (AppDebug.IS_DEBUG) {
							ex.printStackTrace(System.err);
						}
					}
				}
			} else {
				try {
					mBody = new String(mBodyByteArray.toByteArray(), mCharset);
				} catch (UnsupportedEncodingException ex) {
					if (AppDebug.IS_DEBUG) {
						ex.printStackTrace(System.err);
					}
				}
			}
		}
		try {
			if (mBodyByteArray != null) {
				mBodyByteArray.close();
			}
		} catch (IOException e) {
			if (AppDebug.IS_DEBUG) {
				e.printStackTrace();
			}
		}
		mBodyByteArray = null;
	}

	private boolean parseStateLine(String stateLine) {
		if (stateLine != null && stateLine.trim().startsWith("HTTP/")) {
			mStateLine = stateLine;
			String divider = " ";
			String[] strs = stateLine.split(divider);
			for (int i = 0; i < strs.length; i++) {
				String str = strs[i].trim();
				if (TextUtils.isDigitsOnly(str)) {
					mStateCode = Integer.valueOf(str);
					break;
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * 解析HTTP报文头部
	 */
	private void parseHeader(String[] values, int offset) {
		if (values != null) {
			for (int i = offset; i < values.length; i++) {
//				DebugLog.iWithTag("Debug", values[i]);
				String[] keyValues = values[i].split(":");
				if (keyValues.length == 2) {
					String key = keyValues[0];
					String value = keyValues[1];
					if (key.trim().equalsIgnoreCase(KEY_CONTENT_LENGTH)) {
						String contentLength = value.trim();
						headers.put(KEY_CONTENT_LENGTH, contentLength);
						if (TextUtils.isDigitsOnly(contentLength)) {
							mContentLength = Long.valueOf(contentLength);
						}
					} else if (key.trim().equalsIgnoreCase(KEY_CONTENT_TYPE)) {
						mContentType = value;
						headers.put(KEY_CONTENT_TYPE, mContentType);
						String[] typeArrays = mContentType.split(";");
						for (int index = 0; index < typeArrays.length; index++) {
							String[] arrays = typeArrays[index].split("=");
							if (arrays.length == 2
									&& "charset".equalsIgnoreCase(arrays[0].trim())
									&& !TextUtils.isEmpty(arrays[1])) {
								mCharset = arrays[1].trim();
								validateCharset();
							}
						}
					} else if (key.trim().equalsIgnoreCase(KEY_TRANSFER_ENCODING)) {
						mTransferEncoding = value;
						headers.put(KEY_TRANSFER_ENCODING, mTransferEncoding);
					} else if (key.trim().equalsIgnoreCase(KEY_CONTENT_ENCODING)) {
						mContentEncoding = value;
						headers.put(KEY_CONTENT_ENCODING, value);
					} else {
						headers.put(key, value);
					}
				}
			}
		}
	}

	public void setHeaders(HashMap<String, String> headers) {
		this.headers = headers;
		if (headers.get(KEY_CONTENT_TYPE) != null) {
			mContentType = headers.get(KEY_CONTENT_TYPE);
		}
		if (headers.get(KEY_TRANSFER_ENCODING) != null) {
			mTransferEncoding = headers.get(KEY_TRANSFER_ENCODING);
		}
		if (headers.get(KEY_CONTENT_ENCODING) != null) {
			mContentEncoding = headers.get(KEY_CONTENT_ENCODING);
		}
		if (headers.get(KEY_CONTENT_LENGTH) != null) {
			mContentLength = Long.valueOf(headers.get(KEY_CONTENT_LENGTH).trim());
		}
		isParsedHeader = true;
	}

	public void setStateLine(String stateLine) {
		mStateLine = stateLine;
		parseStateLine(mStateLine);
	}

	public String getBody() {
		return mBody;
	}

	public void setBody(String body) {
		mBody = body;
	}

	public ByteBuffer getBuffer() {
		ByteBuffer buffer = null;
		if ((!isShouldAbandon() && isModifiable()) || isBlockInfo) {
			String stateLine = mStateLine + "\r\n";
			String headString = getHeaderString();

			int sum = stateLine.getBytes().length
					+ headString.getBytes().length + mBody.getBytes().length;

			buffer = ByteBuffer.allocate(sum + 2);
			buffer.clear();

			buffer.put(stateLine.getBytes());

			buffer.put(headString.getBytes());

			try {
				Compressor compressor = CompressorFactory.getCompressor(mContentEncoding);
				if (compressor != null) {
					byte[] output = compressor.compress(mBody.getBytes(mCharset));
					if (output != null) {
						buffer.put(output);
					}
				} else {
					buffer.put(mBody.getBytes(mCharset));
				}
			} catch (Exception ex) {
				if (AppDebug.IS_DEBUG) {
					ex.printStackTrace(System.err);
				}
			}

			buffer.flip();
		} else {
			buffer = (ByteBuffer) ByteBuffer.wrap(mData).position(0).limit(position + 1);
		}

		return buffer;
	}


	public String getHeaderString() {
		StringBuilder sb = new StringBuilder("");
		if (TextUtils.isEmpty(mTransferEncoding) &&
				"chunked".equalsIgnoreCase(mTransferEncoding)) {
			headers.remove(KEY_TRANSFER_ENCODING);
		}
		headers.put(KEY_CONTENT_LENGTH, Long.toString(mContentLength));
		Set<String> keySet = headers.keySet();
		for (String key : keySet) {
			if (key.trim().equalsIgnoreCase(KEY_CONTENT_LENGTH)) {
				sb.append(key);
				sb.append(":");
				if (!TextUtils.isEmpty(mContentEncoding)) {
					Compressor compressor = CompressorFactory.getCompressor(mContentEncoding);
					try {
						sb.append(compressor.compress(mBody.getBytes(mCharset)).length);
					} catch (Exception ex) {
						sb.append(mBody.getBytes().length);
					}
				} else {
					sb.append(mBody.getBytes().length);
				}
				sb.append("\r\n");
			} else if (!KEY_TRANSFER_ENCODING.equalsIgnoreCase(key.trim())) {
				sb.append(key);
				sb.append(":");
				sb.append(headers.get(key));
				sb.append("\r\n");
			}
		}
		sb.append("\r\n");

		return sb.toString();
	}

	public int getStateCode() {
		return mStateCode;
	}

	/**
	 * Http回复报文是否可以修改
	 * 1、Https请求的回复，不能修改，因为内容加密
	 * 2、Content-Type类型不为text/html
	 * @return true: 可以修改， false: 不可以修改
	 */
	public boolean isModifiable() {
		return !isBlockInfo && isHtml() && !shouldHaveNoBody();
	}

	public boolean isHtml() {
		return !TextUtils.isEmpty(mContentType)
				&& mContentType.toLowerCase().contains("text/html");
	}

	/**
	 * 不允许带有主体的报文
	 * @return 是否不允许带有实体
	 */
	public boolean shouldHaveNoBody() {
		return (100 <= mStateCode && mStateCode < 200)
				|| mStateCode == 204
				|| mStateCode == 304;
	}

	/**
	 * 如果不是HTML格式的话，要弃用
	 *
	 * @return true: 弃用  false: 不弃用
	 */
	public boolean isShouldAbandon() {
		return isParsedHeader && (!isHtml() || mStateCode != 200);
	}

	/**
	 * 返回报文完整性
	 *
	 * @return true: 报文完整地接收 false: 报文不完整
	 */
	public boolean isCompleted() {
		return isCompleted;
	}

	/**
	 * 校验出错的charset
	 */
	private void validateCharset() {
		if (mCharset != null && "utf8-8".equals(mCharset.trim())) {
			mCharset = "utf-8";
		}
	}

}
