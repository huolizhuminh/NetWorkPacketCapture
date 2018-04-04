package com.minhui.vpn;

import android.net.Network;
import android.util.Log;

import java.io.Closeable;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Created by minhui.zhu on 2017/6/14.
 * Copyright © 2017年 minhui.zhu. All rights reserved.
 */

public class SocketUtils {
    private static String TAG = SocketUtils.class.getSimpleName();

    private static Random random;

    private SocketUtils() {

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



}
