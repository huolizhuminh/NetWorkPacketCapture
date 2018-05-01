package com.minhui.vpn;

import android.content.Context;
import android.net.ConnectivityManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;


/**
 * Created by minhui.zhu on 2017/7/19.
 * Copyright © 2017年 minhui.zhu. All rights reserved.
 */

public class VPNConnectManager {
    private static final String TAG = VPNConnectManager.class.getSimpleName();
    private ConnectivityManager mConnectivityManager;

    private int totalSendPacket = 0;
    private long totalSendByteNum = 0;
    private int totalReceivePacket = 0;
    private long totalReceiveByteNum = 0;
    Map<String, String> hosts = new HashMap<>();
    private ExecutorService executor;
    List<VPNListener> listeners = new ArrayList<>();
    private  Context context;
    private  String name;

    public  void

    init(String appName, Context initContext) {
        this.name=appName;
        this.context=initContext;
        mConnectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetFileManager.getInstance().init(initContext);

    }
    public  Context getContext() {
        return context;
    }

    public  String getAppName() {
        return name;
    }
    public void registerListener(VPNListener listener) {
        listeners.add(listener);
    }

    public void unRegisterListener(VPNListener listener) {
        listeners.remove(listener);
    }

    private static class Inner {
        static VPNConnectManager instance = new VPNConnectManager();
    }

    public void resetNum() {
        totalSendByteNum = 0;
        totalReceiveByteNum = 0;
        totalSendPacket = 0;
        totalReceivePacket = 0;
    }

    public int getTotalReceivePacket() {
        return totalReceivePacket;
    }

    public int getTotalSendPacket() {
        return totalSendPacket;
    }

    void addSendNum(Packet packet, int sendNum) {
        totalSendByteNum = totalSendByteNum + sendNum;
        totalSendPacket++;
        if (listeners.isEmpty()) {
            return;
        }
        VPNListener[] vpnListeners = new VPNListener[listeners.size()];
        listeners.toArray(vpnListeners);
        for (VPNListener listener : vpnListeners) {
            listener.onPacketSend(packet);
        }
    }

    void addReceiveNum(Packet packet, int receiveNum) {
        totalReceiveByteNum = totalReceiveByteNum + receiveNum;
        totalReceivePacket++;
        VPNListener[] vpnListeners = new VPNListener[listeners.size()];
        listeners.toArray(vpnListeners);
        for (VPNListener listener : vpnListeners) {
            listener.onPacketReceive(packet);
        }
    }

    public long getTotalSendNum() {
        return totalSendByteNum;
    }

    public long getTotalReceiveByteNum() {
        return totalReceiveByteNum;
    }

    public List<BaseNetConnection> getAllNetConnection() {
        List<BaseNetConnection> netConnections = null;
        try {
            netConnections = ((LocalVPNService) LocalVPNService.getInstance()).getVpnServer().getNetConnections();
        } catch (Exception e) {

        }
        return netConnections;
    }

    private VPNConnectManager() {

    }

    public static VPNConnectManager getInstance() {
        return Inner.instance;
    }

}
