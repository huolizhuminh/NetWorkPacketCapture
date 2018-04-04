package com.minhui.vpn;

import android.util.Log;

/**
 * Created by minhui.zhu on 2017/10/26.
 * Copyright © 2017年 minhui.zhu. All rights reserved.
 */

public class VPNLog {
    private static boolean isMakeDebugLog = true;
    //open the debug log so you can get the vpn message
    public static void makeDebugLog(boolean isMake) {
        isMakeDebugLog = isMake;
    }

    static void d(String tag, String message) {
        if (isMakeDebugLog) {
            Log.d(tag, message);
        }
    }

    static void v(String tag, String message) {
        Log.v(tag, message);
    }

    static void i(String tag, String message) {
        Log.i(tag, message);
    }

    static void w(String tag, String message) {
        Log.w(tag, message);
    }
    static void w(String tag, String message,Throwable e) {
        Log.w(tag, message,e);
    }
    static void e(String tag, String message) {
        Log.e(tag, message);
    }
}
