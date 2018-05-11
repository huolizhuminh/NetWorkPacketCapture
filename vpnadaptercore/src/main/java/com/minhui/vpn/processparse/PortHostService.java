package com.minhui.vpn.processparse;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.minhui.vpn.BaseNetSession;
import com.minhui.vpn.LocalVPNService;
import com.minhui.vpn.VPNConnectManager;
import com.minhui.vpn.VPNLog;
import com.minhui.vpn.VPNServer;

import java.util.List;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/5/5.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class PortHostService extends Service {
    private static final String ACTION = "action";
    private static final String TAG = "PortHostService";
    private static PortHostService instance;
    private boolean isRefresh;

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

    public static PortHostService getInstance() {
        return instance;
    }


    public List<BaseNetSession> getAndRefreshConnInfo() {
        LocalVPNService instance = LocalVPNService.getInstance();
        if (instance == null) {
            return null;
        }
        VPNServer vpnServer = instance.getVpnServer();
        if (vpnServer == null) {
            return null;
        }
        List<BaseNetSession> netConnections = vpnServer.getNetConnections();
        refreshConnInfo(netConnections);

        return netConnections;

    }

    public void refreshConnInfo() {

        LocalVPNService instance = LocalVPNService.getInstance();
        if (instance == null) {
            return;
        }
        VPNServer vpnServer = instance.getVpnServer();
        if (vpnServer == null) {
            return;
        }
        refreshConnInfo(vpnServer.getNetConnections());

    }

    private void refreshConnInfo(List<BaseNetSession> netConnections) {
        if (isRefresh || netConnections == null) {
            return;
        }
        boolean needRefresh = false;
        for (BaseNetSession connection : netConnections) {
            if (connection.getAppInfo() == null) {
                needRefresh = true;
                break;
            }
        }
        if (!needRefresh) {
            return;
        }
        isRefresh = true;

        NetFileManager.getInstance().refresh();

        for (BaseNetSession connection : netConnections) {
            if (connection.getAppInfo() == null) {
                Integer uid = NetFileManager.getInstance().getUid(connection.getPort());

                if (uid != null) {
                    VPNLog.d(TAG, "can not find uid");
                    connection.setAppInfo(AppInfo.createFromUid(VPNConnectManager.getInstance().getContext(), uid));
                }
            }
        }
        isRefresh = false;

    }


    public static void startParse(Context context) {
        Intent intent = new Intent(context, PortHostService.class);
        context.startService(intent);
    }

    public static void stopParse(Context context) {
        Intent intent = new Intent(context, PortHostService.class);
        context.stopService(intent);
    }
}
