package com.minhui.vpn.builder;

import android.content.Context;
import android.text.TextUtils;



import java.nio.ByteBuffer;
import java.util.HashMap;

/**
 * Created by zengzheying on 16/1/15.
 */
public class HtmlBlockingInfoBuilder implements BlockingInfoBuilder {

	private static final String PLACEHOLDER_TITLE = "{title}";
	private static final String PLACEHOLDER_APP_NAME = "{AppName}";
	private static final String PLACEHOLDER_BLOCK_COUNT = "{BlockCount}";

	private String mHtmlContent;

	@Override
	public ByteBuffer getBlockingInformation() {
		ByteBuffer byteBuffer = DefaultBlockingInfoBuilder.get().getBlockingInformation();
		/*Context context = GlobalApplication.getInstance();
		if (mHtmlContent == null) {
			mHtmlContent = AssetsUtil.readAssetsTextFile(context, "html/block.html");
		}
		if (!TextUtils.isEmpty(mHtmlContent)) {

			AppCache.syncAndIncreaseBlockWithLeanCloud(GlobalApplication.getInstance());

			int count = AppCache.getBlockCount(GlobalApplication.getInstance());
			String result = mHtmlContent.replace(PLACEHOLDER_TITLE, context.getString(R.string.block_title));
			result = result.replace(PLACEHOLDER_APP_NAME, context.getString(R.string.app_name));
			result = result.replace(PLACEHOLDER_BLOCK_COUNT, Integer.toString(count));

			HttpResponse response = new HttpResponse(true);
			HashMap<String, String> header = new HashMap<>();
			header.put("Content-Type", "text/html; charset=utf-8");
			header.put("Connection", "close");
			header.put("Content-Length", Integer.toString(result.getBytes().length));
			response.setHeaders(header);
			response.setBody(result);
			response.setStateLine("HTTP/1.1 200 OK");
			byteBuffer = response.getBuffer();
		} else {
			byteBuffer = DefaultBlockingInfoBuilder.get().getBlockingInformation();
		}*/
		return byteBuffer;
	}
}
