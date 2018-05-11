package com.minhui.vpn;

import android.content.Context;
import android.net.ConnectivityManager;

import com.minhui.vpn.processparse.NetFileManager;
import com.minhui.vpn.processparse.PortHostService;
import com.minhui.vpn.utils.ACache;
import com.minhui.vpn.utils.TimeFormatUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
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
    private Context context;
    private String name;
    private long lastVpnStartTime = 0;
    private String lastVpnStartTimeFormat = null;

    public void init(String appName, Context initContext) {
        this.name = appName;
        this.context = initContext;
        mConnectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetFileManager.getInstance().init(initContext);

    }

    public long getLastVpnStartTime() {
        return lastVpnStartTime;
    }

    public String getLastVpnStartTimeStr() {
        return lastVpnStartTimeFormat;
    }

    public Context getContext() {
        return context;
    }

    public String getAppName() {
        return name;
    }


    public void setLastVpnStartTime(long time) {
        lastVpnStartTime = time;
        lastVpnStartTimeFormat = TimeFormatUtil.formatYYMMDDHHMMSS(lastVpnStartTime);
    }

    public List<BaseNetSession> getAllConn() {
        if (lastVpnStartTimeFormat == null) {
            return null;
        }
        File file = new File(VPNConstants.CONFIG_DIR + lastVpnStartTimeFormat);
        ACache aCache = ACache.get(file);
        String[] list = file.list();
        ArrayList<BaseNetSession> baseNetSessions = new ArrayList<>();
        if(list!=null){

            for (String fileName : list) {
                BaseNetSession netConnection = (BaseNetSession) aCache.getAsObject(fileName);
                baseNetSessions.add(netConnection);
            }
        }

        PortHostService portHostService = PortHostService.getInstance();
        if (portHostService != null) {
            List<BaseNetSession> aliveConnInfo = portHostService.getAndRefreshConnInfo();
            if (aliveConnInfo != null) {
                baseNetSessions.addAll(aliveConnInfo);
            }
        }
        Collections.sort(baseNetSessions, new BaseNetSession.NetConnectionComparator());
        return baseNetSessions;
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
    }

    void addReceiveNum(Packet packet, int receiveNum) {
        totalReceiveByteNum = totalReceiveByteNum + receiveNum;
        totalReceivePacket++;

    }

    public long getTotalSendNum() {
        return totalSendByteNum;
    }

    public long getTotalReceiveByteNum() {
        return totalReceiveByteNum;
    }

    public List<BaseNetSession> getAllNetConnection() {
        List<BaseNetSession> netConnections = null;
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
