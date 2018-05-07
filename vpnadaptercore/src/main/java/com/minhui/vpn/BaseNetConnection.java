package com.minhui.vpn;

import android.os.SystemClock;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/2/27.
 *         Copyright © 2017年 minhui.zhu. All rights reserved.
 */

public class BaseNetConnection implements Serializable {
    public static final String TCP = "TCP";
    public static final String UDP = "UPD";
    public String type;
    public String ipAndPort;
    public String hostName;
    public long sendByteNum;
    public long receiveByteNum;
    public long receivePacketNum;
    public long sendPacketNum;
    public long refreshTime = System.currentTimeMillis();
    public AppInfo appInfo;
    public boolean isSSL;
    public int port;
    public String url;
    public long connectionStartTime = System.currentTimeMillis();
    protected transient Vector<ConversationData> conversation = new Vector<>();
    public long vpnStartTime;

    public BaseNetConnection(BaseNetConnection connection) {
        type = connection.type;
        ipAndPort = connection.ipAndPort;
        hostName = connection.hostName;
        sendByteNum = connection.sendByteNum;
        receiveByteNum = connection.receiveByteNum;
        receivePacketNum = connection.receivePacketNum;
        sendPacketNum = connection.sendPacketNum;
        refreshTime = connection.refreshTime;
        appInfo = connection.appInfo;
        isSSL = connection.isSSL;
        port = connection.port;
        url = connection.url;
        connectionStartTime = connection.connectionStartTime;
        conversation = connection.conversation;
        vpnStartTime = connection.vpnStartTime;
    }

    public BaseNetConnection() {

    }

    public static class NetConnectionComparator implements Comparator<BaseNetConnection> {

        @Override
        public int compare(BaseNetConnection o1, BaseNetConnection o2) {
            if (o1 == o2) {
                return 0;
            }
            return Long.compare(o2.refreshTime, o1.refreshTime);
        }
    }

    public String getUniqueName() {
        String uinID = ipAndPort + connectionStartTime;
        return String.valueOf(uinID.hashCode());
    }

    @Override
    public String toString() {
        return "BaseNetConnection{" +
                "type='" + type + '\'' +
                ", ipAndPort='" + ipAndPort + '\'' +
                ", sendByteNum=" + sendByteNum +
                ", receiveByteNum=" + receiveByteNum +
                ", receivePacketNum=" + receivePacketNum +
                ", sendPacketNum=" + sendPacketNum +
                ", refreshTime=" + refreshTime +
                '}';
    }
}
