package com.minhui.vpn.utils;

import android.support.annotation.Nullable;

/**
 * Created by zengzheying on 15/12/31.
 */
public class CompressorFactory {

	public static final String METHOD_GZIP = "gzip";

	@Nullable
	public static Compressor getCompressor(String method) {
		Compressor compressor = null;
		if (method != null) {
			if (method.trim().equals(METHOD_GZIP)) {
				compressor = new GZipCompressor();
			}
		}

		return compressor;
	}

}
