package com.minhui.vpn.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;

import com.minhui.vpn.VPNLog;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;

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
    private static final String URLENCODED = "urlencoded";

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
            String line = buffer.readUtf8LineStrict(headerLimit);
            StringBuilder headBuilder = new StringBuilder();
            while (line != null && line.length() > 0) {
                headerLimit = HEADER_LIMIT - line.length();
                String[] split = line.split(":");
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
                if (s.equals(GZIP)) {
                    showData.bodyStr = getGzipStr(buffer);
                    return showData;
                }
            }
            if (contentType != null) {
                if (contentType.toLowerCase().contains(IMAGE)) {
                    byte[] bytes = buffer.readByteArray();
                    try {
                        showData.bodyImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    } catch (Exception e) {
                        Log.d(TAG, "error parse map");
                    }
                    if (showData.bodyImage == null) {
                        showData.bodyStr = new String(bytes, 0, bytes.length, "utf-8");
                    }

                    return showData;
                } else if (contentType.toLowerCase().contains(URLENCODED)) {
                    String readUtf8 = buffer.readUtf8();
                    showData.bodyStr = URLDecoder.decode(readUtf8);
                    return showData;
                }

            }
            showData.bodyStr = buffer.readUtf8();
            VPNLog.d(TAG, "bodyStr is " + showData.bodyStr);
            return showData;
        } catch (Exception e) {
            VPNLog.d(TAG, "parseSaveFile " + e.getMessage());
            return getRawDataFromFile(childFile);
        }

    }

    private static ShowData getRawDataFromFile(File childFile) {
        Source fileSource = null;
        ShowData showData = new ShowData();
        try {
            String name = childFile.getName();
            showData.isRequest = name.contains(TcpDataSaveHelper.REQUEST);
            fileSource = Okio.source(childFile);
            BufferedSource buffer = Okio.buffer(fileSource);
            showData.headStr = buffer.readUtf8();
            VPNLog.d(TAG, showData.headStr);
            return showData;
        } catch (Exception e) {
            Log.d(TAG, "failed to getRawDataFromFile" + e.getMessage());
            e.printStackTrace();
            return null;
        }

    }

    private static String getGzipStr(BufferedSource buffer) {
        GzipSource gzipSource = new GzipSource(buffer);
        BufferedSource gzipBuffer = Okio.buffer(gzipSource);
        byte[] bytes = null;
        try {
            bytes = gzipBuffer.readByteArray();
            String s = new String(bytes, 0, bytes.length, "utf-8");
            VPNLog.d(TAG, "s is" + s);
            return s;
        } catch (IOException e) {
            try {
                if (bytes != null) {
                    String showStr = new String(bytes, 0, bytes.length);
                    VPNLog.d(TAG, "showStr is" + showStr);
                    return showStr;

                }
            } catch (Exception newError) {
                VPNLog.d(TAG, "failed to getGzipStr");
            }


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
            return TextUtils.isEmpty(bodyStr) && bodyImage == null;
        }
    }
}
