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
    ;


    static class NetConnectionComparator implements Comparator<BaseNetConnection> {

        @Override
        public int compare(BaseNetConnection o1, BaseNetConnection o2) {
            if (o1 == o2) {
                return 0;
            }
            return (int) (o2.refreshTime - o1.refreshTime);
        }
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
