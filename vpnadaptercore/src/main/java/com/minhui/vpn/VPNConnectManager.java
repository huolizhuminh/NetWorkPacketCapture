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

import java.util.List;


/**
 * Created by minhui.zhu on 2017/7/19.
 * Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class VPNConnectManager {
    private int cellularNetId = -1;
    private int wifiNetID = -1;
    private static final String TAG = VPNConnectManager.class.getSimpleName();
    private ConnectivityManager.NetworkCallback cellularCallBack;
    private ConnectivityManager.NetworkCallback wifiCallBack;
    private ConnectivityManager mConnectivityManager;
    private String setDeviceAddress;
    private String connectIPAddress;
    private int totalSendPacket = 0;
    private long totalSendByteNum = 0;
    private int totalReceivePacket = 0;
    private long totalReceiveByteNum = 0;

    public boolean isWifiAvailable() {
        return wifiNetID != -1;
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

    public void addSendNum(int sendNum) {
        totalSendByteNum = totalSendByteNum + sendNum;
        totalSendPacket++;
    }

    public void addReceiveNum(int receiveNum) {
        totalReceiveByteNum = totalReceiveByteNum + receiveNum;
        totalReceivePacket++;
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

    int getCellularNetId() {
        return cellularNetId;
    }

    public void initNetWork() {
        if (Build.VERSION.SDK_INT < 21) {
            return;
        }
        initCellular();
        initWifi();
    }

    private void initWifi() {
        if (Build.VERSION.SDK_INT < 21) {
            return;
        }
        VPNLog.d(TAG, "initWifi");
        if (wifiCallBack != null) {
            unRegisterNetworkCallBack(wifiCallBack);
        }
        final NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();
        wifiCallBack = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network requestNetwork) {
                try {
                    wifiNetID = SocketUtils.getNetID(requestNetwork);
                    VPNLog.i(TAG, "wifi is  available wifi net id is:" + wifiNetID);
                } catch (Exception e) {
                    VPNLog.e(TAG, "failed to get wifi netId error is" + e);
                }
                refreshConnectDeviceIP();
            }

            @Override
            public void onLost(Network network) {
                wifiNetID = -1;
                VPNLog.i(TAG, "wifi is  lost");
            }
        };
        mConnectivityManager.requestNetwork(networkRequest, wifiCallBack);
    }

    /*对蜂窝网络的监听并没有打开app就一直监听，主要是为了考虑兼容华为的某些机型。*/
    private void initCellular() {
        if (Build.VERSION.SDK_INT < 21) {
            return;
        }
        VPNLog.d(TAG, "initCellular");
        if (cellularCallBack != null) {
            unRegisterNetworkCallBack(cellularCallBack);
        }
        final NetworkRequest celluarNetworkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build();
        cellularCallBack = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network requestNetwork) {
                try {
                    cellularNetId = SocketUtils.getNetID(requestNetwork);
                    //  EventBus.getDefault().post(new MobileAvailableChangeVo());
                    VPNLog.i(TAG, "mobile is Available cellularNetId is :" + cellularNetId);
                } catch (Exception e) {
                    VPNLog.e(TAG, "failed to get mobile net id error is" + e);
                }

            }

            @Override
            public void onLost(Network network) {
                cellularNetId = -1;
                //  EventBus.getDefault().post(new MobileAvailableChangeVo());
                VPNLog.i(TAG, "mobile is  Lost");
            }
        };
        mConnectivityManager.requestNetwork(celluarNetworkRequest, cellularCallBack);

    }

    int getWifiNetID() {
        return wifiNetID;
    }

    public boolean isMobileNetAvailable() {
        return cellularNetId != -1;
    }

    public void unRegisterNetWorkCallBacks() {
        if (Build.VERSION.SDK_INT < 21) {
            return;
        }
        unRegisterNetworkCallBack(wifiCallBack);
        unRegisterNetworkCallBack(cellularCallBack);
    }

    private void unRegisterNetworkCallBack(ConnectivityManager.NetworkCallback callback) {
        if (callback == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < 21) {
            return;
        }
        try {
            mConnectivityManager.unregisterNetworkCallback(callback);
        } catch (Exception e) {
            VPNLog.w(TAG, "failed to unregisterNetworkCallback" + e.getStackTrace());
        }
    }
}
