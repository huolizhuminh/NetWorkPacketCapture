package com.minhui.vpn;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Created by minhui.zhu on 2017/7/19.
 * Copyright © 2017年 minhui.zhu. All rights reserved.
 */

public class VPNConnectManager {
    private static final String TAG = VPNConnectManager.class.getSimpleName();
    private ConnectivityManager mConnectivityManager;
    private String setDeviceAddress;
    private String connectIPAddress;
    private int totalSendPacket = 0;
    private long totalSendByteNum = 0;
    private int totalReceivePacket = 0;
    private long totalReceiveByteNum = 0;
    Map<String, String> hosts = new HashMap<>();
    private ExecutorService executor;
    List<VPNListener> listeners = new ArrayList<>();

    public String getHostName(final InetAddress sourceAddress) {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor();
        }
        String hostName = hosts.get(sourceAddress.getHostAddress());
        if (hostName == null) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {

                        String hostName = sourceAddress.getHostName();
                        hosts.put(sourceAddress.getHostAddress(), hostName);

                    } catch (Exception e) {
                        Log.d(TAG, "failed to getHostName" + e.getMessage());
                    }
                }
            });
        }
        return hostName;
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

    public List<NetConnection> getAllNetConnection() {
        List<NetConnection> netConnections = null;
        try {
            netConnections = ((LocalVPNService) LocalVPNService.getInstance()).getVpnServer().getNetConnections();
        } catch (Exception e) {

        }
        return netConnections;
    }

    private VPNConnectManager() {
        mConnectivityManager = (ConnectivityManager) LocalVpnInit.getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public static VPNConnectManager getInstance() {
        return Inner.instance;
    }

    public void intDeviceAddress(String ipAddress) {
        this.setDeviceAddress = ipAddress;
    }

    boolean isDeviceAddress(String ip) {
        if (ip == null) return false;
        Log.d(TAG, "ip" + ip + "set:" + setDeviceAddress + "connect" + connectIPAddress);
        return (!TextUtils.isEmpty(setDeviceAddress) && setDeviceAddress.equals(ip)) ||
                (!TextUtils.isEmpty(connectIPAddress) && connectIPAddress.equals(ip));

    }

    public void refreshConnectDeviceIP() {
        WifiManager wifiManager = (WifiManager) LocalVpnInit.getContext().getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);

        DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
        if (dhcpInfo == null) {
            Log.d(TAG, "dhcpInfo is null");
            return;
        }
        int initIP = dhcpInfo.serverAddress;
        //此处获取ip为整数类型，需要进行转换
        connectIPAddress = intToIp(initIP);
        Log.d(TAG, "refreshConnectDeviceIP ip=" + connectIPAddress);
    }


    private String intToIp(int i) {
        return (i & 0xFF) + "." + ((i >> 8) & 0xFF) + "." + ((i >> 16) & 0xFF) + "."
                + ((i >> 24) & 0xFF);
    }


}
