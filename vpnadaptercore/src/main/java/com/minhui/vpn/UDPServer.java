package com.minhui.vpn;

import android.net.VpnService;
import android.util.Log;

import com.minhui.vpn.tunnel.UDPTunnel;
import com.minhui.vpn.utils.MyLRUCache;
import com.minhui.vpn.utils.SocketUtils;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by minhui.zhu on 2017/7/13.
 * Copyright © 2017年 minhui.zhu. All rights reserved.
 */

public class UDPServer  {
    private String TAG = UDPServer.class.getSimpleName();
    private VpnService vpnService;
    private ConcurrentLinkedQueue<Packet> outputQueue;
    private Selector selector;

    private boolean isClose = false;

    private static final int MAX_UDP_CACHE_SIZE = 50;
    private final MyLRUCache<String, UDPTunnel> udpConnections =
            new MyLRUCache<>(MAX_UDP_CACHE_SIZE, new MyLRUCache.CleanupCallback<String, UDPTunnel>() {
                @Override
                public void cleanUp(Map.Entry<String, UDPTunnel> eldest) {
                    VPNLog.d(TAG, "clean up updConn " + eldest.getValue().getIpAndPort());
                    eldest.getValue().close();
                }

            });


    public UDPServer(VpnService vpnService, ConcurrentLinkedQueue<Packet> outputQueue, Selector selector) {
        this.vpnService = vpnService;
        this.outputQueue = outputQueue;
        this.selector = selector;

    }


    public void processUDPPacket(Packet packet) {
        String ipAndPort = packet.getIpAndPort();
        UDPTunnel udpConn = getUDPConn(ipAndPort);
        if (udpConn == null) {
            VPNLog.d(TAG, "upd no conn ipAndPort:" + ipAndPort);
            udpConn = new UDPTunnel(vpnService, selector, this, packet, outputQueue);
            putUDPConn(ipAndPort, udpConn);
            udpConn.initConnection();
        } else {
            VPNLog.d(TAG, "upd packet ipAndPort:" + ipAndPort);
            udpConn.processPacket(packet);
        }
    }





   public void closeAllUDPConn() {
        synchronized (udpConnections) {
            Iterator<Map.Entry<String, UDPTunnel>> it = udpConnections.entrySet().iterator();
            while (it.hasNext()) {
                it.next().getValue().close();
                it.remove();
            }
        }
    }


   public void closeUDPConn(UDPTunnel connection) {
        synchronized (udpConnections) {
            connection.close();
            udpConnections.remove(connection.getIpAndPort());
        }
    }

    public  UDPTunnel getUDPConn(String ipAndPort) {
        synchronized (udpConnections) {
            return udpConnections.get(ipAndPort);
        }
    }

    void putUDPConn(String ipAndPort, UDPTunnel connection) {
        synchronized (udpConnections) {
            udpConnections.put(ipAndPort, connection);
        }

    }

}
