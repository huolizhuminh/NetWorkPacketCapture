package com.zhixin.roav.vpnadaptercore;

import android.net.Network;
import android.os.Build;
import android.util.Log;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.DatagramSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Created by minhui.zhu on 2017/6/14.
 * Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class SocketUtils {
    private static String TAG = SocketUtils.class.getSimpleName();

    private static Random random;

    private SocketUtils() {

    }

    static int bindSocket(final DatagramSocket socket, int netId) {
        if (netId == -1) {
            return -1;
        }
        if (Build.VERSION.SDK_INT < 21) {
            return -1;
        }
        try {
            Class datagramSocketClass = Class.forName("java.net.DatagramSocket");
            Method getDeclaredMethod = datagramSocketClass.getDeclaredMethod("getFileDescriptor$");

            getDeclaredMethod.setAccessible(true);
            FileDescriptor fileDescriptor = (FileDescriptor) getDeclaredMethod.invoke(socket);
            return bindSocket(getFileDedId(fileDescriptor), netId);
        } catch (Exception e) {
            Log.w(TAG, "failed to bind Socket to Net error is :" + e.getMessage());
            return -1;
        }

    }

    static int bindSocket(final Socket socket, int netId) {
        if (netId == -1) {
            return -1;
        }
        if (Build.VERSION.SDK_INT < 21) {
            return -1;
        }
        try {
            Class socketClass = Class.forName("java.net.Socket");
            Method getDeclaredMethod = socketClass.getDeclaredMethod("getFileDescriptor$");
            getDeclaredMethod.setAccessible(true);
            FileDescriptor fileDescriptor = (FileDescriptor) getDeclaredMethod.invoke(socket);
            return bindSocket(getFileDedId(fileDescriptor), netId);
        } catch (Exception e) {
            Log.e(TAG, "failed to bind Socket to Net error is :" + e.getMessage());
            return -1;
        }

    }

    private static int getFileDedId(FileDescriptor fileDescriptor) throws Exception {

        Class fileDesClass = Class.forName("java.io.FileDescriptor");
        Method getId = fileDesClass.getDeclaredMethod("getInt$");
        getId.setAccessible(true);
        int fileDesId = (int) getId.invoke(fileDescriptor);
        Log.d(TAG, "fileDesId is" + fileDesId);
        return fileDesId;
    }

    private static int bindSocket(final int socket, int netId) {
        try {
            Class netWorkUtilsClass = Class.forName("android.net.NetworkUtils");
            Method bindMethod = netWorkUtilsClass.getDeclaredMethod("bindSocketToNetwork", int.class, int.class);
            bindMethod.setAccessible(true);
            Integer bindResult = (Integer) bindMethod.invoke(null, socket, netId);
            return bindResult;
        } catch (Exception e) {
            Log.e(TAG, "failed to bind Socket to Net error is :" + e.getMessage());
            return -1;
        }
    }

    static int getNetID(Network requestNetwork) throws Exception {
        Class networkClass = Class.forName("android.net.Network");
        Field netIdField = networkClass.getDeclaredField("netId");
        netIdField.setAccessible(true);
        return netIdField.getInt(requestNetwork);
    }

    static void closeResources(Closeable... resources) {
        for (Closeable resource : resources) {
            try {
                if (resource != null) {
                    resource.close();
                }
            } catch (Exception e) {
                Log.d(TAG, "failed to close resource error is:" + e.getMessage());
            }
        }
    }


    static long getRandomSequence() {
        return getRandom().nextInt(Short.MAX_VALUE + 1);
    }

    private static Random getRandom() {
        if (random == null) {
            random = new Random();
        }
        return random;
    }

    static ByteBuffer getByteBuffer() {
        return ByteBuffer.allocate(VPNConstants.BUFFER_SIZE);
    }


    public static int strongBindWifiSocket(Socket socket) {
        int bindResult = -1;
        int netId = VPNConnectManager.getInstance().getWifiNetID();
        bindResult = bindSocket(socket, netId);
        Log.i(TAG, " try strongBindWifiSocket:" + bindResult);
        if (bindResult == 0) {
            Log.i(TAG, "strongBindWifiSocket bindResult:" + bindResult);
            return bindResult;
        }
        for (int i = 0; i < 15; i++) {
            try {
                Thread.sleep(300);
            } catch (Exception e) {

            }
            netId = VPNConnectManager.getInstance().getWifiNetID();
            bindResult = bindSocket(socket, netId);
            Log.i(TAG, " try strongBindWifiSocket:" + bindResult);
            if (bindResult == 0) {
                break;
            }
        }
        Log.i(TAG, "strongBindWifiSocket bindResult:" + bindResult);
        return bindResult;
    }
}
