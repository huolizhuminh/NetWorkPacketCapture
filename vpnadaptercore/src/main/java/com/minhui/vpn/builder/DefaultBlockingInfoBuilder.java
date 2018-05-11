package com.minhui.vpn.builder;


import com.minhui.vpn.http.HttpResponse;

import java.nio.ByteBuffer;
import java.util.HashMap;

/**
 * Created by zengzheying on 16/1/15.
 */
public class DefaultBlockingInfoBuilder implements BlockingInfoBuilder {

	private static DefaultBlockingInfoBuilder sInstance;

	public static DefaultBlockingInfoBuilder get() {
		if (sInstance == null) {
			sInstance = new DefaultBlockingInfoBuilder();
		}
		return sInstance;
	}

	@Override
	public ByteBuffer getBlockingInformation() {
		HttpResponse httpResponse = new HttpResponse(false);
		HashMap<String, String> header = new HashMap<>();
		String body = "<html><header><title>该页面已被拦截</title></header><body>该页面已被拦截</body></html>";
		header.put("Content-Type", "text/html; charset=utf-8");
		header.put("Connection", "close");
		header.put("Content-Length", Integer.toString(body.getBytes().length));
		httpResponse.setHeaders(header);
		httpResponse.setBody(body);
		httpResponse.setStateLine("HTTP/1.1 200 OK");
		return httpResponse.getBuffer();
	}
}
