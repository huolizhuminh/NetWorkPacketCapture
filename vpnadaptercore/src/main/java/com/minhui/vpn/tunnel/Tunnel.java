package com.minhui.vpn.tunnel;


import com.minhui.vpn.http.HttpResponse;
import com.minhui.vpn.utils.AppDebug;
import com.minhui.vpn.utils.DebugLog;
import com.minhui.vpn.utils.VpnServiceHelper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * Created by zengzheying on 15/12/29.
 */
public abstract class Tunnel {

	final static ByteBuffer GL_BUFFER = ByteBuffer.allocate(20000);
	public static long SessionCount;
	protected InetSocketAddress mDestAddress;
	protected boolean isRemoteTunnel = false;
	private SocketChannel mInnerChannel; //自己的Channel
	private ByteBuffer mSendRemainBuffer; //发送数据缓存
	private Selector mSelector;
	private HttpResponse mHttpResponse; //http报文
	private boolean isHttpsRequest = false;
	/**
	 * 与外网的通信两个Tunnel负责，一个负责Apps与TCP代理服务器的通信，一个负责TCP代理服务器
	 * 与外网服务器的通信，Apps与外网服务器的数据交换靠这两个Tunnel来进行
	 */
	private Tunnel mBrotherTunnel;
	private boolean mDisposed;
	private InetSocketAddress mServerEP;

	public Tunnel(SocketChannel innerChannel, Selector selector) {
		mInnerChannel = innerChannel;
		mSelector = selector;
		SessionCount++;
	}

	public Tunnel(InetSocketAddress serverAddress, Selector selector) throws IOException {
		SocketChannel innerChannel = SocketChannel.open();
		innerChannel.configureBlocking(false);
		this.mInnerChannel = innerChannel;
		this.mSelector = selector;
		this.mServerEP = serverAddress;
		SessionCount++;
	}

	/**
	 * 方法调用次序：
	 * connect() -> onConnectable() -> onConnected()[子类实现]
	 * beginReceived() ->  onReadable() -> afterReceived()[子类实现]
	 */

	protected abstract void onConnected(ByteBuffer buffer) throws Exception;

	protected abstract boolean isTunnelEstablished();

	protected abstract void beforeSend(ByteBuffer buffer) throws Exception;

	protected abstract void afterReceived(ByteBuffer buffer) throws Exception;

	protected abstract void onDispose();

	public void setBrotherTunnel(Tunnel brotherTunnel) {
		this.mBrotherTunnel = brotherTunnel;
	}


	public void connect(InetSocketAddress destAddress) throws Exception {
		if (VpnServiceHelper.protect(mInnerChannel.socket())) { //保护socket不走VPN
			mDestAddress = destAddress;
			mInnerChannel.register(mSelector, SelectionKey.OP_CONNECT, this); //注册连接事件
			mInnerChannel.connect(mServerEP);
			DebugLog.i("Connecting to %s", mServerEP);
		} else {
			throw new Exception("VPN protect socket failed.");
		}
	}

	public void onConnectable() {
		try {
			if (mInnerChannel.finishConnect()) {
				onConnected(GL_BUFFER); //通知子类TCP已连接，子类可以根据协议实现握手等
				DebugLog.i("Connected to %s", mServerEP);
			} else {
				DebugLog.e("Connect to %s failed.", mServerEP);
				this.dispose();
			}
		} catch (Exception e) {
			if (AppDebug.IS_DEBUG) {
				e.printStackTrace(System.err);
			}
			DebugLog.e("Connect to %s failed: %s", mServerEP, e);
			this.dispose();
		}
	}

	protected void beginReceived() throws Exception {
		if (mInnerChannel.isBlocking()) {
			mInnerChannel.configureBlocking(false);
		}
		mInnerChannel.register(mSelector, SelectionKey.OP_READ, this); //注册读事件
	}

	public void onReadable(SelectionKey key) {
		try {
			ByteBuffer buffer = GL_BUFFER;
			buffer.clear();
			int bytesRead = mInnerChannel.read(buffer);
			if (bytesRead > 0) {
				buffer.flip();
				afterReceived(buffer); //先让子类处理，例如解密数据
				if (isRemoteTunnel && !isHttpsRequest) { //外网发过来的数据，需要进行内容过滤
					if (mHttpResponse == null) {
						if (buffer.limit() - buffer.position() > 5) {
							ByteBuffer httpBuffer = ByteBuffer.wrap(buffer.array(), buffer.position(),
									buffer.limit() - buffer.position());
							int oldPosition = httpBuffer.position();
							byte[] firstFiveBytes = new byte[5];
							httpBuffer.get(firstFiveBytes);
							httpBuffer.position(oldPosition);
							if ("HTTP/".equals(new String(firstFiveBytes))) { //HTTP报文回复
								mHttpResponse = new HttpResponse(isHttpsRequest);
								mHttpResponse.write(httpBuffer);
							}
						}
					} else {
						mHttpResponse.write(buffer);
					}

					if (mHttpResponse != null) {
						ByteBuffer httpBuffer = null;
						if (mHttpResponse.isShouldAbandon()) {
							httpBuffer = mHttpResponse.getBuffer();
						} else if (mHttpResponse.isCompleted()) {  //已经完整地接收了HTTP报文
							String body = mHttpResponse.getBody();
//							if (body.contains("4566")) {
//								mHttpResponse.setBody(body.replace("4566", "修改之后的标题"));
//							}
							if (body.contains("小说pk小妾我做妃")) {
								DebugLog.iWithTag("DebugIP", "what the fuck!!!!!");
							}
							httpBuffer = mHttpResponse.getBuffer();
						}
						if (httpBuffer != null) {
							sendToBrother(key, httpBuffer);
							mHttpResponse = null; //节约内存
						}
					} else { //回复的报文不是http ~T T~
						sendToBrother(key, buffer);
					}
				} else {
					sendToBrother(key, buffer);
				}

			} else if (bytesRead < 0) {
				if (mHttpResponse != null) {
					ByteBuffer httpBuffer = mHttpResponse.getBuffer();
					sendToBrother(key, httpBuffer);
					mHttpResponse = null;
				}
				this.dispose();
			}
		} catch (Exception ex) {
			if (AppDebug.IS_DEBUG) {
				ex.printStackTrace(System.err);
			}
			DebugLog.e("onReadable catch an exception: %s", ex);
			this.dispose();
		}
	}

	protected void sendToBrother(SelectionKey key, ByteBuffer buffer) throws Exception {
		if (isTunnelEstablished() && buffer.hasRemaining()) { //将读到的数据，转发给兄弟
			mBrotherTunnel.beforeSend(buffer); //发送之前，先让子类处理，例如做加密等。
			if (!mBrotherTunnel.write(buffer, true)) {
				key.cancel(); //兄弟吃不消，就取消读取事件
				DebugLog.w("%s can not read more.\n", mServerEP);
			}
		}
	}

	protected boolean write(ByteBuffer buffer, boolean copyRemainData) throws Exception {
		int byteSent;
		while (buffer.hasRemaining()) {
			byteSent = mInnerChannel.write(buffer);
			if (byteSent == 0) {
				break; //不能再发送了，终止循环
			}
		}

		if (buffer.hasRemaining()) { //数据没有发送完毕
			if (copyRemainData) { //拷贝剩余数据，然后侦听写入事件，待可写入时写入
				//拷贝剩余数据
				if (mSendRemainBuffer == null) {
					mSendRemainBuffer = ByteBuffer.allocate(buffer.capacity());
				}
				mSendRemainBuffer.clear();
				mSendRemainBuffer.put(buffer);
				mSendRemainBuffer.flip();
				mInnerChannel.register(mSelector, SelectionKey.OP_WRITE, this); //注册写事件
			}
			return false;
		} else { //发送完毕了
			return true;
		}
	}


	public void onWritable(SelectionKey key) {
		try {
			this.beforeSend(mSendRemainBuffer); //发送之前，先让子类处理，例如做加密等
			if (this.write(mSendRemainBuffer, false)) { //如果剩余数据已经发送完毕
				key.cancel();
				if (isTunnelEstablished()) {
					mBrotherTunnel.beginReceived(); //这边数据发送完毕，通知兄弟可以收数据了
				} else {
					this.beginReceived(); //开始接受代理服务器的响应数据
				}
			}
		} catch (Exception ex) {
			if (AppDebug.IS_DEBUG) {
				ex.printStackTrace(System.err);
			}

			DebugLog.e("onWritable catch an exception: %s", ex);

			this.dispose();
		}
	}

	protected void onTunnelEstablished() throws Exception {
		this.beginReceived(); //开始接收数据
		mBrotherTunnel.beginReceived(); //兄弟也开始接收数据吧
	}

	public void dispose() {
		disposeInternal(true);
	}

	void disposeInternal(boolean disposeBrother) {
		if (!mDisposed) {
			try {
				mInnerChannel.close();
			} catch (Exception ex) {
				if (AppDebug.IS_DEBUG) {
					ex.printStackTrace(System.err);
				}
				DebugLog.e("InnerChannel close catch an exception: %s", ex);
			}

			if (mBrotherTunnel != null && disposeBrother) {
				mBrotherTunnel.disposeInternal(false); //把兄弟的资源也释放了
			}

			mInnerChannel = null;
			mSendRemainBuffer = null;
			mSelector = null;
			mBrotherTunnel = null;
			mHttpResponse = null;
			mDisposed = true;
			SessionCount--;

			onDispose();
		}
	}

	public void setIsHttpsRequest(boolean isHttpsRequest) {
		this.isHttpsRequest = isHttpsRequest;
	}

	public boolean isHttpsRequest() {
		return isHttpsRequest;
	}

	public void sendBlockInformation() throws IOException {
	/*	ByteBuffer buffer = ProxyConfig.Instance.getBlockingInfo();
		mInnerChannel.write(buffer);*/
	}
}
