package com.minhui.vpn.utils;

import android.support.annotation.Nullable;

/**
 * Created by zengzheying on 16/1/2.
 */
public interface Compressor {

	@Nullable
	byte[] compress(byte[] source) throws Exception;

	@Nullable
	byte[] uncompress(byte[] cipher) throws Exception;
}
