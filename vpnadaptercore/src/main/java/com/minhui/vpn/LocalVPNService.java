package com.minhui.vpn;
/**
 * Created by minhui.zhu on 2017/6/24.
 * Copyright © 2017年 minhui.zhu. All rights reserved.
 */

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.nio.channels.Selector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalVPNService extends VpnService {
    public static final String ACTION_START_VPN = "com.minhui.START_VPN";
    public static final String ACTION_CLOSE_VPN = "com.minhui.roav.CLOSE_VPN";
    private static final String FACEBOOK_APP = "com.facebook.katana";
    private static final String YOUTUBE_APP = "com.google.android.youtube";
    private static final String GOOGLE_MAP_APP = "com.google.android.apps.maps";

    private static final String TAG = LocalVPNService.class.getSimpleName();
    private static final String VPN_ADDRESS = "10.0.0.2"; // Only IPv4 support for now
    private static final String VPN_ROUTE = "0.0.0.0"; // Intercept everything
    private static final String GOOGLE_DNS_FIRST = "8.8.8.8";
    private static final String GOOGLE_DNS_SECOND = "8.8.4.4";
    private static final String AMERICA = "208.67.222.222";
    private static final String HK_DNS_SECOND = "205.252.144.228";
    private static final String CHINA_DNS_FIRST = "114.114.114.114";
    public static final String BROADCAST_VPN_STATE = "com.minhui.localvpn.VPN_STATE";
    public static final String SELECT_PACKAGE_ID = "select_protect_package_id";
    private static VpnService instance;

    private ParcelFileDescriptor vpnInterface = null;

    private ConcurrentLinkedQueue<Packet> networkToDeviceQueue;
    private ExecutorService executorService = Executors.newFixedThreadPool(2);
    private Selector selector;
    private static boolean isRunning = false;
    private VPNServer vpnServer;
    private VPNClient vpnInPutRunnable;
    private String selectPackage;

    @Override
    public void onCreate() {
        super.onCreate();

    }

    public static boolean isRunning() {
        return isRunning;
    }

    private void setupVPN() {
        Builder builder = new Builder();
        builder.addAddress(VPN_ADDRESS, 32);
        builder.addRoute(VPN_ROUTE, 0);
        //某些国外的手机例如google pixel 默认的dns解析器地址不是8.8.8.8 ，不设置会出错
        builder.addDnsServer(GOOGLE_DNS_FIRST);
        builder.addDnsServer(CHINA_DNS_FIRST);
        builder.addDnsServer(GOOGLE_DNS_SECOND);
        builder.addDnsServer(AMERICA);
        builder.setMtu(1280);
        try {
            if (selectPackage != null) {
                builder.addAllowedApplication(selectPackage);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
      /*   addAllowedApp(builder, YOUTUBE_APP);*/
        vpnInterface = builder.setSession(LocalVpnInit.getAppName()).establish();
    }

    private void addAllowedApp(Builder builder, String appName) {
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                builder.addAllowedApplication(appName);
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "vpn failed to allow application,app is " + appName + "error is" + e.getMessage());
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_START_VPN.equals(action)) {
            if (isRunning) {
                return START_STICKY;
            }
            selectPackage = intent.getStringExtra(SELECT_PACKAGE_ID);
            startLocalVPN();
        } else {
            if (!isRunning) {
                return START_STICKY;
            }
            cleanup();
        }
        return START_STICKY;
    }

    private void startLocalVPN() {

        try {
            isRunning = true;
            setupVPN();
            instance = this;
            selector = Selector.open();
            networkToDeviceQueue = new ConcurrentLinkedQueue<>();

            vpnServer = new VPNServer(this, networkToDeviceQueue, selector);
            vpnInPutRunnable = new VPNClient(vpnInterface.getFileDescriptor(), vpnServer, networkToDeviceQueue, selector);
            executorService.submit(vpnInPutRunnable);
            executorService.submit(vpnServer);
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(BROADCAST_VPN_STATE));
            Log.i(TAG, "Started");
        } catch (Exception e) {
            Log.w(TAG, "Error starting service", e);
            cleanup();
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Stopped");
    }

    private void cleanup() {
        Log.i(TAG, "clean up");
        isRunning = false;
        networkToDeviceQueue = null;
        closeRunnable(vpnServer);
        closeRunnable(vpnInPutRunnable);
        SocketUtils.closeResources(vpnInterface);
        instance = null;
    }

    private void closeRunnable(CloseableRun run) {
        if (run != null) {
            run.closeRun();
        }
    }


    @Override
    public void onRevoke() {
        super.onRevoke();
        cleanup();
    }

    public static VpnService getInstance() {
        return instance;
    }

    public VPNServer getVpnServer() {
        return vpnServer;
    }
}