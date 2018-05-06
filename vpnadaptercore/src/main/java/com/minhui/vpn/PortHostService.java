package com.minhui.vpn;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/5/5.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class PortHostService extends Service {
    private static final String ACTION = "action";
    private static final int ACTION_START = 0;
    private static final int ACTION_STOP = 1;
    private static final String TAG = "PortHostService";
    boolean isParsing = false;
    private ScheduledExecutorService scheduledExecutorService;
    private static PortHostService instance;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }
    public static PortHostService getInstance(){
        return instance;
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int action = intent.getIntExtra(ACTION, ACTION_STOP);
        switch (action) {
            case ACTION_START:
                startParseFile();
                break;
            default:
                stopParseFile();
                break;
        }

        return super.onStartCommand(intent, flags, startId);

    }

    private void stopParseFile() {


        if (!isParsing) {
            return;
        }
        isParsing = false;
        if (scheduledExecutorService == null) {
            return;
        }
        try {
            scheduledExecutorService.shutdownNow();
        } catch (Exception e) {
            VPNLog.e(TAG, "stopParseFile error is" + e.getMessage());
        }


    }

    private void startParseFile() {
        if (isParsing) {
            return;
        }
        isParsing = true;
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                refreshConnectionAppInfo();


            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);


    }

    public List<BaseNetConnection> refreshConnectionAppInfo() {
        NetFileManager.getInstance().refresh();
        VPNServer vpnServer = LocalVPNService.getInstance().getVpnServer();
        if (vpnServer == null) {
            return null;
        }
        List<BaseNetConnection> netConnections = vpnServer.getNetConnections();
        for (BaseNetConnection connection : netConnections) {
            if (connection.appInfo == null) {
                Integer uid = NetFileManager.getInstance().getUid(connection.port);

                if (uid != null) {
                    VPNLog.d(TAG, "can not find uid");
                    connection.appInfo = AppInfo.createFromUid(VPNConnectManager.getInstance().getContext(), uid);
                }
            }
        }
        return netConnections;

    }

    public static void startParse(Context context) {
        Intent intent = new Intent(context, PortHostService.class);
        intent.putExtra(ACTION, ACTION_START);
        context.startService(intent);
    }

    public static void stopParse(Context context) {
        Intent intent = new Intent(context, PortHostService.class);
        intent.putExtra(ACTION, ACTION_STOP);
        context.startService(intent);
    }
}
