package com.minhui.vpn;

import android.net.VpnService;
import android.util.Log;

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

class VPNServer implements CloseableRun {
    private String TAG = VPNServer.class.getSimpleName();
    private static final int MAX_CACHE_TCP_SIZE = 20; // XXX: Is this ideal?
    VpnService vpnService;
    ConcurrentLinkedQueue<Packet> outputQueue;
    Selector selector;
    private final Object tcpLock = new Object();
    private final MyLRUCache<String, TCPConnection> tcpCache =
            new MyLRUCache<>(MAX_CACHE_TCP_SIZE, new MyLRUCache.CleanupCallback<String, TCPConnection>() {
                @Override
                public void cleanup(Map.Entry<String, TCPConnection> eldest) {
                    VPNLog.d(TAG, "clean up tcpConn " + eldest.getValue().getIpAndPort());
                    eldest.getValue().closeChannelAndClearCache();
                }
            });

    private boolean isClose = false;

    private static final int MAX_UDP_CACHE_SIZE = 50;
    private final MyLRUCache<String, UDPConnection> udpConnections =
            new MyLRUCache<>(MAX_UDP_CACHE_SIZE, new MyLRUCache.CleanupCallback<String, UDPConnection>() {
                @Override
                public void cleanup(Map.Entry<String, UDPConnection> eldest) {
                    VPNLog.d(TAG, "clean up updConn " + eldest.getValue().getIpAndPort());
                    eldest.getValue().close();
                }
            });


    VPNServer(VpnService vpnService, ConcurrentLinkedQueue<Packet> outputQueue, Selector selector) {
        this.vpnService = vpnService;
        this.outputQueue = outputQueue;
        this.selector = selector;

    }

    void processPacket(Packet packet) {
        if (packet == null) {
            return;
        }

        if (packet.isTCP()) {
            processTCPPacket(packet);
        } else if (packet.isUDP()) {
            processUDPPacket(packet);
        }
    }

    private void processUDPPacket(Packet packet) {
        String ipAndPort = packet.getIpAndPort();
        UDPConnection udpConn = getUDPConn(ipAndPort);
        if (udpConn == null) {
            VPNLog.d(TAG, "upd no conn ipAndPort:" + ipAndPort);
            udpConn = new UDPConnection(vpnService, selector, this, packet, outputQueue);
            putUDPConn(ipAndPort, udpConn);
            udpConn.initConnection();
        } else {
            VPNLog.d(TAG, "upd packet ipAndPort:" + ipAndPort);
            udpConn.processPacket(packet);
        }
    }

    private void processTCPPacket(Packet packet) {
        String ipAndPort = packet.getIpAndPort();
        TCPConnection tcpConnection = getTCPConn(ipAndPort);
        Packet.TCPHeader tcpHeader = packet.tcpHeader;
        if (tcpConnection == null) {
            VPNLog.d(TAG, "tp: " + tcpHeader.flags + "ack" + tcpHeader.acknowledgementNumber + "seq:"
                    + tcpHeader.sequenceNumber + "ip: " + ipAndPort);
            if (tcpHeader.isSYN()) {
                tcpConnection = new TCPConnection(vpnService, selector, this, packet, outputQueue);
                putTCPConn(ipAndPort, tcpConnection);
                tcpConnection.initConnection();
            } else {
                packet.swapSourceAndDestination();
                ByteBuffer responseBuffer = SocketUtils.getByteBuffer();
                packet.updateTCPBuffer(responseBuffer, (byte) Packet.TCPHeader.RST,
                        0, tcpHeader.sequenceNumber + 1, 0);
            }
        } else {
            VPNLog.d(TAG, "tp: " + tcpHeader.flags + "ak:" + tcpHeader.acknowledgementNumber + "win:" + tcpHeader.getWindow() + "seq:"
                    + tcpHeader.sequenceNumber + "mySq:" + tcpConnection.mySequenceNum + "myAk:" + tcpConnection.myAcknowledgementNum
                    + ",sz:" + packet.playLoadSize + "ip: " + ipAndPort);
            tcpConnection.processSendPacket(packet);
        }

    }

    @Override
    public void run() {
        while (true) {
            try {
                if (isRunClose()) {
                    break;
                }
                int readyChannels = selector.select();

                if (readyChannels == 0) {
                    Thread.sleep(5);
                    continue;
                }
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = keys.iterator();
                while (keyIterator.hasNext() && !isRunClose()) {
                    SelectionKey key = keyIterator.next();
                    if (key.isValid()) {
                        String ipAndPort = (String) key.attachment();
                        TCPConnection tcpConn = getTCPConn(ipAndPort);
                        if (tcpConn != null) {
                            tcpConn.getKeyHandler().onKeyReady(key);
                        } else {
                            UDPConnection udpConn = getUDPConn(ipAndPort);
                            if (udpConn != null) {
                                udpConn.getKeyHandler().onKeyReady(key);
                            }
                        }
                    }
                    keyIterator.remove();
                }
                // keys.clear();
            } catch (Exception e) {
                VPNLog.w(TAG, e.toString(), e);
            }
        }
        closeAllTCPConn();
        closeAllUDPConn();
        SocketUtils.closeResources(selector);

    }

    TCPConnection getTCPConn(String ipAndPort) {
        synchronized (tcpLock) {
            return tcpCache.get(ipAndPort);
        }

    }

    void putTCPConn(String ipAndPort, TCPConnection tcpConnection) {
        synchronized (tcpLock) {
            tcpCache.put(ipAndPort, tcpConnection);
        }
    }

    void closeTCPConnection(TCPConnection tcpConnection) {
        synchronized (tcpLock) {
            VPNLog.d(TAG, "closeTCPConnection ipAndPort " + tcpConnection.getIpAndPort());
            tcpConnection.closeChannelAndClearCache();
            tcpCache.remove(tcpConnection.getIpAndPort());
        }
    }

    private void closeAllTCPConn() {

        synchronized (tcpLock) {

            Iterator<Map.Entry<String, TCPConnection>> it = tcpCache.entrySet().iterator();
            int i=0;
            int y=tcpCache.size();
            while (it.hasNext()) {
                TCPConnection tcpConnection = it.next().getValue();
                tcpConnection.closeChannelAndClearCache();
                it.remove();
                i++;
            }
            Log.d(TAG,"closeAllTCPConn iterate time "+i+"  cache size "+y);

        }

    }


    void closeAllUDPConn() {
        synchronized (udpConnections) {
            Iterator<Map.Entry<String, UDPConnection>> it = udpConnections.entrySet().iterator();
            while (it.hasNext()) {
                it.next().getValue().close();
                it.remove();
            }
        }
    }


    void closeUDPConn(UDPConnection connection) {
        synchronized (udpConnections) {
            connection.close();
            udpConnections.remove(connection.getIpAndPort());
        }
    }

    UDPConnection getUDPConn(String ipAndPort) {
        synchronized (udpConnections) {
            return udpConnections.get(ipAndPort);
        }
    }

    void putUDPConn(String ipAndPort, UDPConnection connection) {
        synchronized (udpConnections) {
            udpConnections.put(ipAndPort, connection);
        }

    }


    @Override
    public void closeRun() {
        isClose = true;
    }

    @Override
    public boolean isRunClose() {
        return isClose;
    }


    interface KeyHandler {
        void onKeyReady(SelectionKey key);
    }

    public List<BaseNetConnection> getNetConnections() {
        List<BaseNetConnection> netConnections = new ArrayList<>();


        synchronized (tcpLock) {
            Iterator<Map.Entry<String, TCPConnection>> it = tcpCache.entrySet().iterator();
            while (it.hasNext()) {
                TCPConnection connection = it.next().getValue();
                checkAndAddConn(connection, netConnections);

            }
        }
        synchronized (udpConnections) {
            Iterator<Map.Entry<String, UDPConnection>> it = udpConnections.entrySet().iterator();
            while (it.hasNext()) {
                UDPConnection udpConnection = it.next().getValue();
                checkAndAddConn(udpConnection, netConnections);

            }
        }
        Collections.sort(netConnections, new BaseNetConnection.NetConnectionComparator());


        return netConnections;
    }

    private void checkAndAddConn(BaseNetConnection connection, List<BaseNetConnection> netConnections) {
        String packageName = VPNConnectManager.getInstance().getContext().getPackageName();
        String selectPackage = ((LocalVPNService) LocalVPNService.getInstance()).getSelectPackage();

        if (connection.appInfo == null) {
            netConnections.add(connection);
            return;
        }
        String capturePkg = connection.appInfo.pkgs.getAt(0);

        if (connection.appInfo != null && packageName.equals(capturePkg)) {
            return;
        }
        if (selectPackage != null && !selectPackage.equals(capturePkg)) {
            return;
        }

        netConnections.add(connection);
    }
}
