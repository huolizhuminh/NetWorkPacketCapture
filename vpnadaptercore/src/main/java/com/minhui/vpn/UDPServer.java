package com.minhui.vpn;

import android.net.VpnService;
import android.util.Log;

import com.minhui.vpn.tunnel.UDPTunnel;
import com.minhui.vpn.utils.AppDebug;
import com.minhui.vpn.utils.DebugLog;
import com.minhui.vpn.utils.MyLRUCache;
import com.minhui.vpn.utils.SocketUtils;

import java.io.IOException;
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

public class UDPServer implements Runnable {
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


    public void start() {
        Thread thread = new Thread(this, "UDPServer");
        thread.start();
    }

    public UDPServer(VpnService vpnService, ConcurrentLinkedQueue<Packet> outputQueue) {
        try {
            selector = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    public UDPTunnel getUDPConn(String ipAndPort) {
        synchronized (udpConnections) {
            return udpConnections.get(ipAndPort);
        }
    }

    void putUDPConn(String ipAndPort, UDPTunnel connection) {
        synchronized (udpConnections) {
            udpConnections.put(ipAndPort, connection);
        }

    }

    @Override
    public void run() {
        try {
            while (true) {
                int select = selector.select();
                if (select == 0) {
                    Thread.sleep(5);
                }
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    if (key.isValid()) {
                        try {
                            Object attachment = key.attachment();
                            if (attachment instanceof KeyHandler) {
                                ((KeyHandler) attachment).onKeyReady(key);
                            }

                        } catch (Exception ex) {
                            if (AppDebug.IS_DEBUG) {
                                ex.printStackTrace(System.err);
                            }

                            DebugLog.e("TcpProxyServer iterate SelectionKey catch an exception: %s", ex);
                        }
                    }
                    keyIterator.remove();
                }


            }
        } catch (Exception e) {
            if (AppDebug.IS_DEBUG) {
                e.printStackTrace(System.err);
            }

            DebugLog.e("TcpProxyServer catch an exception: %s", e);
        } finally {
            this.stop();
            DebugLog.i("TcpServer thread exited.");
        }
    }

    private void stop() {
        try {
            selector.close();
            selector = null;
        } catch (Exception e) {

        }
    }

}
