package com.zhixin.roav.vpnadaptercore;

import android.net.VpnService;
import android.os.SystemClock;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by minhui.zhu on 2017/7/13.
 * Copyright © 2017年 Oceanwing. All rights reserved.
 */

class VPNServer implements CloseableRun {
    private final Timer timer;
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
                    synchronized (tcpLock) {
                        if (tcpConnectionArrayList.contains(eldest.getValue())) {
                            tcpConnectionArrayList.remove(eldest.getValue());
                        }
                    }
                }
            });

    private final ArrayList<TCPConnection> tcpConnectionArrayList = new ArrayList<>();
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
        timer = new Timer();
        timer.schedule(new ChannelTimerTask(), 1000, 1000);
    }

    void processPacket(Packet packet) {
        if (packet == null) {
            return;
        }

        if (packet.isTCP()) {
            processTCPPacket(packet);
        } else {
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
        if (timer != null) {
            timer.purge();
            timer.cancel();
        }
    }

    TCPConnection getTCPConn(String ipAndPort) {
        synchronized (tcpLock) {
            return tcpCache.get(ipAndPort);
        }

    }

    void putTCPConn(String ipAndPort, TCPConnection tcpConnection) {
        synchronized (tcpLock) {
            tcpCache.put(ipAndPort, tcpConnection);
            tcpConnectionArrayList.add(tcpConnection);
        }
    }

    void closeTCPConnection(TCPConnection tcpConnection) {
        synchronized (tcpLock) {
            VPNLog.d(TAG, "closeTCPConnection ipAndPort " + tcpConnection.getIpAndPort());
            tcpConnection.closeChannelAndClearCache();
            tcpCache.remove(tcpConnection.getIpAndPort());
            tcpConnectionArrayList.remove(tcpConnection);
        }
    }

    private void closeAllTCPConn() {
        synchronized (tcpLock) {
            Iterator<TCPConnection> iterator = tcpConnectionArrayList.iterator();
            while (iterator.hasNext()) {
                TCPConnection tcpConnection = iterator.next();
                tcpConnection.closeChannelAndClearCache();
                tcpCache.remove(tcpConnection.getIpAndPort());
                iterator.remove();
            }
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


    class ChannelTimerTask extends TimerTask {

        @Override
        public void run() {
            clearTimeOutChannel();
        }

        private void clearTimeOutChannel() {

            long currentTime = SystemClock.currentThreadTimeMillis();
            synchronized (tcpLock) {
                Iterator<TCPConnection> iterator = tcpConnectionArrayList.iterator();
                while (iterator.hasNext()) {
                    TCPConnection tcpConnection = iterator.next();
                    if (tcpConnection.needClear(currentTime)) {
                        tcpConnection.closeChannelAndClearCache();
                        tcpCache.remove(tcpConnection.getIpAndPort());
                        iterator.remove();
                    }
                }
            }
        }
    }

    interface KeyHandler {
        void onKeyReady(SelectionKey key);
    }

    public List<NetConnection> getNetConnections() {
        List<NetConnection> netConnections = new ArrayList<>();
        synchronized (tcpLock) {
            Iterator<TCPConnection> iterator = tcpConnectionArrayList.iterator();
            while (iterator.hasNext()) {
                TCPConnection tcpConnection = iterator.next();
                NetConnection connection = new NetConnection();
                connection.type = "TCP";
                connection.ipAndPort = tcpConnection.ipAndPort;
                connection.receiveNum = tcpConnection.receiveNum;
                connection.receivePacketNum = tcpConnection.receivePacketNum;
                connection.sendNum = tcpConnection.sendNum;
                connection.sendPacketNum = tcpConnection.sendPacketNum;
                connection.refreshTime = tcpConnection.refreshTime;
                netConnections.add(connection);
            }
        }
        synchronized (udpConnections) {
            Iterator<Map.Entry<String, UDPConnection>> it = udpConnections.entrySet().iterator();
            while (it.hasNext()) {
                UDPConnection udpConnection = it.next().getValue();
                NetConnection connection = new NetConnection();
                connection.type = "UPD";
                connection.ipAndPort = udpConnection.ipAndPort;
                connection.receiveNum = udpConnection.receiveNum;
                connection.receivePacketNum = udpConnection.receivePacketNum;
                connection.sendNum = udpConnection.sendNum;
                connection.sendPacketNum = udpConnection.sendPacketNum;
                connection.refreshTime = udpConnection.refreshTime;
                netConnections.add(connection);
            }
        }
        Collections.sort(netConnections);
        return netConnections;
    }
}
