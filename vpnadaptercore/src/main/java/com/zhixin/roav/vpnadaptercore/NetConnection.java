package com.zhixin.roav.vpnadaptercore;

import android.support.annotation.NonNull;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/2/27.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class NetConnection implements Comparable{
    public String type;
    public String ipAndPort;
    public long sendNum;
    public long receiveNum;
    public long receivePacketNum;
    public long sendPacketNum;
    public long refreshTime;
    @Override
    public int compareTo(@NonNull Object o) {
        NetConnection connection=(NetConnection)o;
        return (int) (((NetConnection) o).refreshTime -refreshTime);
    }

    @Override
    public String toString() {
        return "NetConnection{" +
                "type='" + type + '\'' +
                ", ipAndPort='" + ipAndPort + '\'' +
                ", sendNum=" + sendNum +
                ", receiveNum=" + receiveNum +
                ", receivePacketNum=" + receivePacketNum +
                ", sendPacketNum=" + sendPacketNum +
                ", refreshTime=" + refreshTime +
                '}';
    }
}
