package com.minhui.vpn.utils;

import com.minhui.vpn.VPNLog;
import com.minhui.vpn.utils.ThreadProxy;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/5/7.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class TcpDataSaveHelper {
    private static final String TAG = "TcpDataSaveHelper";
    private String dir;
    private SaveData lastSaveData;
    private File lastSaveFile;
    int requestNum = 0;
    int responseNum = 0;
    public static final String REQUEST = "request";
    public static final String RESPONSE = "response";

    public TcpDataSaveHelper(String dir) {
        this.dir = dir;
    }

    public void addData(final SaveData data) {
        ThreadProxy.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                if (lastSaveData == null || (lastSaveData.isRequest ^ data.isRequest)) {
                    newFileAndSaveData(data);
                } else {
                    appendFileData(data);
                }
                lastSaveData = data;
            }
        });

    }

    private void appendFileData(SaveData data) {
        RandomAccessFile randomAccessFile;
        try {
            randomAccessFile = new RandomAccessFile(lastSaveFile.getAbsolutePath(), "rw");
            long length = randomAccessFile.length();
            randomAccessFile.seek(length);
            randomAccessFile.write(data.needParseData, data.offSet, data.playoffSize);
        } catch (Exception e) {

        }
    }

    private void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                VPNLog.d(TAG, "failed to close closeable");
            }
        }
    }

    private void newFileAndSaveData(SaveData data) {
        int saveNum;
        if (data.isRequest) {
            saveNum = requestNum;
            requestNum++;
        } else {
            saveNum = responseNum;
            responseNum++;
        }
        File file = new File(dir);
        if (!file.exists()) {
            file.mkdirs();
        }
        String childName = (data.isRequest ? REQUEST : RESPONSE) + saveNum;
        lastSaveFile = new File(file, childName);
        FileOutputStream fileOutputStream = null;

        try {
            fileOutputStream = new FileOutputStream(lastSaveFile);
            fileOutputStream.write(data.needParseData, data.offSet, data.playoffSize);
            fileOutputStream.flush();
        } catch (Exception e) {
            VPNLog.d(TAG, "failed to saveData" + e.getMessage());
        } finally {
            close(fileOutputStream);
        }

    }


    public static class SaveData {
        boolean isRequest;
        byte[] needParseData;
        int offSet;
        int playoffSize;

        private SaveData(Builder builder) {
            isRequest = builder.isRequest;
            needParseData = builder.needParseData;
            offSet = builder.offSet;
            playoffSize = builder.length;
        }


        public static final class Builder {
            private boolean isRequest;
            private byte[] needParseData;
            private int offSet;
            private int length;

            public Builder() {
            }

            public Builder isRequest(boolean val) {
                isRequest = val;
                return this;
            }

            public Builder needParseData(byte[] val) {
                needParseData = val;
                return this;
            }

            public Builder offSet(int val) {
                offSet = val;
                return this;
            }

            public Builder length(int val) {
                length = val;
                return this;
            }

            public SaveData build() {
                return new SaveData(this);
            }
        }
    }
}
