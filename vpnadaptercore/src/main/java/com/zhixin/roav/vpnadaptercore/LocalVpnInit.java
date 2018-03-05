package com.zhixin.roav.vpnadaptercore;

import android.content.Context;

/**
 * Created by minhui.zhu on 2017/10/26.
 * Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class LocalVpnInit {
    private static Context context;
    private static String name;

    public static void init(String appName, Context initContext) {
        init(appName, initContext,null);
    }

    public static void init(String appName, Context initContext, String deviceIP) {
        name = appName;
        context = initContext;
        VPNConnectManager.getInstance().intDeviceAddress(deviceIP);
    }

    public static Context getContext() {
        return context;
    }

    public static String getAppName() {
        return name;
    }
}
