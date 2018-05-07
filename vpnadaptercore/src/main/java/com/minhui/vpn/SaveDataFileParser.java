package com.minhui.vpn;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.preference.PreferenceActivity;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import okio.BufferedSource;
import okio.GzipSource;
import okio.Okio;
import okio.Source;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/5/7.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class SaveDataFileParser {


    private static final String TAG = "SaveDataFileParser";
    private static final int HEADER_LIMIT = 256 * 1024;
    private static final String CONTENT_ENCODING = "Content-Encoding";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String GZIP = "gzip";
    private static final String IMAGE = "image";

    /**
     * 参照okhttp对请求和响应进行解析
     *
     * @param childFile
     * @return
     */
    public static ShowData parseSaveFile(File childFile) {
        try {
            String encodingType = null;
            String contentType = null;
            long headerLimit = HEADER_LIMIT;
            String name = childFile.getName();
            ShowData showData = new ShowData();
            if (name.contains(TcpDataSaveHelper.REQUEST)) {
                showData.isRequest = true;
            }
            Source fileSource = Okio.source(childFile);
            BufferedSource buffer = Okio.buffer(fileSource);
            HashMap<String, String> headMap = new HashMap<>();
            String line = buffer.readUtf8LineStrict(headerLimit);
            StringBuilder headBuilder = new StringBuilder();
            while (line != null && line.length() > 0) {
                headerLimit = HEADER_LIMIT - line.length();
                String[] split = line.split(":");
                if (split.length > 1) {
                    headMap.put(split[0], split[1]);
                }
                if (CONTENT_ENCODING.equalsIgnoreCase(split[0])) {
                    encodingType = split[1];
                }
                if (CONTENT_TYPE.equalsIgnoreCase(split[0])) {
                    contentType = split[1];
                }
                headBuilder.append(line).append("\n");
                line = buffer.readUtf8LineStrict(headerLimit);
            }
            showData.headStr = headBuilder.toString();

            if (encodingType != null) {
                String s = encodingType.toLowerCase();
                if (s.contains(GZIP)) {
                    showData.bodyStr = getGzipStr(buffer);
                    return showData;
                }
            }
            if (contentType != null) {
                if (contentType.toLowerCase().contains(IMAGE)) {
                    byte[] bytes = buffer.readByteArray();
                    showData.bodyImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                }


            }
            showData.bodyStr = buffer.readUtf8();
            VPNLog.d(TAG, "bodyStr is " + showData.bodyStr);
            return showData;
        } catch (Exception e) {
            VPNLog.d(TAG, "parseSaveFile " + e.getMessage());
        }
        return null;

    }

    private static String getGzipStr(BufferedSource buffer) {
        GzipSource gzipSource = new GzipSource(buffer);
        BufferedSource gzipBuffer = Okio.buffer(gzipSource);
        try {
            String s = gzipBuffer.readUtf8();
            return s;
        } catch (IOException e) {
            VPNLog.d(TAG, "failed to getGzipStr");
        }
        return null;
    }

    public static class ShowData {
        boolean isRequest;
        String headStr;
        String bodyStr;
        Bitmap bodyImage;

        public boolean isRequest() {
            return isRequest;
        }

        public String getHeadStr() {
            return headStr;
        }

        public String getBodyStr() {
            return bodyStr;
        }

        public Bitmap getBodyImage() {
            return bodyImage;
        }

        public boolean isBodyNull() {
            return bodyStr == null && bodyImage == null;
        }
    }
}
