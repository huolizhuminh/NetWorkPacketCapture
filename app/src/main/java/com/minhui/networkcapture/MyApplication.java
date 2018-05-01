package com.minhui.networkcapture;

import android.app.Application;

import com.minhui.vpn.VPNConnectManager;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/4/30.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        VPNConnectManager.getInstance().init(getString(R.string.app_name),getApplicationContext());
    }
}
