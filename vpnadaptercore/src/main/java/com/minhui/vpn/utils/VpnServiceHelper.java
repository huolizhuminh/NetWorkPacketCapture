package com.minhui.vpn.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;


import com.minhui.vpn.VPNConstants;
import com.minhui.vpn.nat.NatSession;
import com.minhui.vpn.processparse.PortHostService;
import com.minhui.vpn.service.FirewallVpnService;
import com.minhui.vpn.tcpip.IPHeader;
import com.minhui.vpn.tcpip.UDPHeader;

import java.io.File;
import java.net.DatagramSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by zengzheying on 16/1/12.
 */
public class VpnServiceHelper {
    static Context context;
    public static final int START_VPN_SERVICE_REQUEST_CODE = 2015;
    private static FirewallVpnService sVpnService;
    private static SharedPreferences sp;

    public static void onVpnServiceCreated(FirewallVpnService vpnService) {
        sVpnService = vpnService;
        if(context==null){
            context=vpnService.getApplicationContext();
        }
    }

    public static void onVpnServiceDestroy() {
        sVpnService = null;
    }


    public static Context getContext() {
        return context;
    }

    public static boolean isUDPDataNeedSave() {

        sp = context.getSharedPreferences(VPNConstants.VPN_SP_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(VPNConstants.IS_UDP_NEED_SAVE, false);
    }

    public static boolean protect(Socket socket) {
        if (sVpnService != null) {
            return sVpnService.protect(socket);
        }
        return false;
    }

    public static boolean protect(DatagramSocket socket) {
        if (sVpnService != null) {
            return sVpnService.protect(socket);
        }
        return false;
    }

    public static boolean vpnRunningStatus() {
        if (sVpnService != null) {
            return sVpnService.vpnRunningStatus();
        }
        return false;
    }

    public static void changeVpnRunningStatus(Context context, boolean isStart) {
        if (context == null) {
            return;
        }
        if (isStart) {
            Intent intent = FirewallVpnService.prepare(context);
            if (intent == null) {
                startVpnService(context);
            } else {
                if (context instanceof Activity) {
                    ((Activity) context).startActivityForResult(intent, START_VPN_SERVICE_REQUEST_CODE);
                }
            }
        } else if (sVpnService != null) {
            boolean stopStatus = false;
            sVpnService.setVpnRunningStatus(stopStatus);
        }
    }
    public static List<NatSession> getAllSession() {
        if (FirewallVpnService.lastVpnStartTimeFormat == null) {
            return null;
        }
        try {
            File file = new File(VPNConstants.CONFIG_DIR +FirewallVpnService. lastVpnStartTimeFormat);
            ACache aCache = ACache.get(file);
            String[] list = file.list();
            ArrayList<NatSession> baseNetSessions = new ArrayList<>();
            if(list!=null){

                for (String fileName : list) {
                    NatSession netConnection = (NatSession) aCache.getAsObject(fileName);
                    baseNetSessions.add(netConnection);
                }
            }

            PortHostService portHostService = PortHostService.getInstance();
            if (portHostService != null) {
                List<NatSession> aliveConnInfo = portHostService.getAndRefreshSessionInfo();
                if (aliveConnInfo != null) {
                    baseNetSessions.addAll(aliveConnInfo);
                }
            }
            Collections.sort(baseNetSessions, new NatSession.NatSesionComparator());
            return baseNetSessions;
        }catch (Exception e){
            return null;
        }

    }
    public static void startVpnService(Context context) {
        if (context == null) {
            return;
        }

        context.startService(new Intent(context, FirewallVpnService.class));
    }
}
