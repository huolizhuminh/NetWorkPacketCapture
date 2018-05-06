package com.minhui.vpn;

import android.net.VpnService;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by minhui.zhu on 2017/7/11.
 * Copyright © 2017年 minhui.zhu. All rights reserved.
 */

public class UDPConnection extends BaseNetConnection {


    private static final String TAG = UDPConnection.class.getSimpleName();
    private final VpnService vpnService;
    private final Selector selector;
    private final VPNServer vpnServer;
    private final VPNServer.KeyHandler keyHandler;
    private final Queue<Packet> outputQueue;
    private Packet referencePacket;
    private SelectionKey selectionKey;

    private DatagramChannel channel;
    private final ConcurrentLinkedQueue<Packet> toNetWorkPackets = new ConcurrentLinkedQueue<>();
    private static final int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.UDP_HEADER_SIZE;


    public UDPConnection(VpnService vpnService, Selector selector, VPNServer vpnServer, Packet packet, Queue<Packet> outputQueue) {
        this.vpnService = vpnService;
        this.selector = selector;
        this.vpnServer = vpnServer;
        this.referencePacket = packet;
        ipAndPort = packet.getIpAndPort();
        this.outputQueue = outputQueue;
        this.keyHandler = new VPNServer.KeyHandler() {
            @Override
            public void onKeyReady(SelectionKey key) {
                processKey(key);
            }
        };
        port = packet.udpHeader.sourcePort;
        type = UDP;
    }

    private String hostName;

    public String getHostName() {

        return hostName;

    }

    private void processKey(SelectionKey key) {
        if (key.isWritable()) {
            processSend();
        } else if (key.isReadable()) {
            processReceived();
        }
        updateInterests();
    }

    private void processReceived() {
        VPNLog.d(TAG, "processReceived:" + ipAndPort);
        ByteBuffer receiveBuffer = SocketUtils.getByteBuffer();
        // Leave space for the header
        receiveBuffer.position(HEADER_SIZE);
        int readBytes = 0;
        try {
            readBytes = channel.read(receiveBuffer);
        } catch (Exception e) {
            VPNLog.d(TAG, "failed to read udp datas ");
            vpnServer.closeUDPConn(this);
            return;
        }
        if (readBytes == -1) {
            vpnServer.closeUDPConn(this);
            VPNLog.d(TAG, "read  data error :" + ipAndPort);
        } else if (readBytes == 0) {
            VPNLog.d(TAG, "read no data :" + ipAndPort);
        } else {
            VPNLog.d(TAG, "read readBytes:" + readBytes + "ipAndPort:" + ipAndPort);
            Packet newPacket = referencePacket.duplicated();
            newPacket.updateUDPBuffer(receiveBuffer, readBytes);
            receiveBuffer.position(HEADER_SIZE + readBytes);
            outputQueue.offer(newPacket);
            VPNLog.d(TAG, "read  data :readBytes:" + readBytes + "ipAndPort:" + ipAndPort);
            VPNConnectManager.getInstance().addReceiveNum(newPacket, readBytes);
            receivePacketNum++;
            receiveByteNum = receiveByteNum + readBytes;
            refreshTime = System.currentTimeMillis();
        }
    }

    private void processSend() {
        VPNLog.d(TAG, "processWriteUDPData " + ipAndPort);
        Packet toNetWorkPacket = getToNetWorkPackets();
        if (toNetWorkPacket == null) {
            VPNLog.d(TAG, "write data  no packet ");
            return;
        }
        try {
            ByteBuffer payloadBuffer = toNetWorkPacket.backingBuffer;

            VPNConnectManager.getInstance().addSendNum(toNetWorkPacket, payloadBuffer.limit() - payloadBuffer.position());
            sendPacketNum++;
            sendByteNum = sendByteNum + payloadBuffer.limit() - payloadBuffer.position();
            refreshTime = System.currentTimeMillis();
            while (payloadBuffer.hasRemaining())
                channel.write(payloadBuffer);

        } catch (IOException e) {
            VPNLog.w(TAG, "Network write error: " + ipAndPort, e);
            vpnServer.closeUDPConn(this);
        }
    }

    void initConnection() {
        VPNLog.d(TAG, "init  ipAndPort:" + ipAndPort);
        InetAddress destinationAddress = referencePacket.ip4Header.destinationAddress;
        int destinationPort = referencePacket.udpHeader.destinationPort;
        try {
            channel = DatagramChannel.open();
            vpnService.protect(channel.socket());
            VPNLog.i(TAG, "bind Socket result is :" + "ipAndPort is " + ipAndPort);
            channel.configureBlocking(false);
            channel.connect(new InetSocketAddress(destinationAddress, destinationPort));
            selector.wakeup();
            selectionKey = channel.register(selector,
                    SelectionKey.OP_READ, ipAndPort);
        } catch (IOException e) {
            SocketUtils.closeResources(channel);
            VPNLog.w(TAG, "Connection error: " + ipAndPort, e);
            return;
        }
        referencePacket.swapSourceAndDestination();
        addToNetWorkPacket(referencePacket);
    }

    void processPacket(Packet packet) {
        addToNetWorkPacket(packet);
        updateInterests();
    }

    void close() {
        try {
            selectionKey.cancel();
            channel.close();
        } catch (IOException e) {
            VPNLog.w(TAG, "error to close UDP channel IpAndPort" + ipAndPort + ",error is " + e.getMessage());
        }

    }

    String getIpAndPort() {

        return ipAndPort;
    }

    Packet getToNetWorkPackets() {
        return toNetWorkPackets.poll();
    }

    void addToNetWorkPacket(Packet packet) {
        toNetWorkPackets.offer(packet);
        updateInterests();
    }

    DatagramChannel getChannel() {
        return channel;
    }

    void updateInterests() {
        int ops;
        if (toNetWorkPackets.isEmpty()) {
            ops = SelectionKey.OP_READ;
        } else {
            ops = SelectionKey.OP_WRITE | SelectionKey.OP_READ;
        }
        selector.wakeup();
        selectionKey.interestOps(ops);
        VPNLog.d(TAG, "updateInterests ops:" + ops + ",ip" + ipAndPort);
    }

    Packet getReferencePacket() {
        return referencePacket;
    }


    VPNServer.KeyHandler getKeyHandler() {
        return keyHandler;
    }
}
