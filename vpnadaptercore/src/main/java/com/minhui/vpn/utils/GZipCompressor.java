package com.minhui.vpn.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Created by zengzheying on 15/12/31.
 */
public class GZipCompressor implements Compressor {

	/**
	 * GZIP压缩
	 *
	 * @param source 源数组
	 * @return 压缩数组
	 * @throws Exception
	 */
	public byte[] compress(byte[] source) throws Exception {
		if (source == null || source.length == 0) {
			return source;
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		GZIPOutputStream gzip = new GZIPOutputStream(out);
		gzip.write(source);
		gzip.close();
		return out.toByteArray();
	}

	/**
	 * GZIP解压
	 *
	 * @param cipher 压缩字节数组
	 * @return 源数组
	 * @throws Exception
	 */
	public byte[] uncompress(byte[] cipher) throws Exception {
		if (cipher == null || cipher.length == 0) {
			return cipher;
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayInputStream in = new ByteArrayInputStream(cipher);
		GZIPInputStream gunzip = new GZIPInputStream(in);
		byte[] buffer = new byte[1024];
		int n;
		while ((n = gunzip.read(buffer)) >= 0) {
			out.write(buffer, 0, n);
		}
		gunzip.close();
		return out.toByteArray();
	}

}
