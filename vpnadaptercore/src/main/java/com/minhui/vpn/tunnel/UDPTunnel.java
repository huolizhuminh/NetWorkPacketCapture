package com.minhui.vpn.tunnel;

import android.net.VpnService;
import android.os.Handler;
import android.os.Looper;

import com.minhui.vpn.KeyHandler;
import com.minhui.vpn.Packet;
import com.minhui.vpn.UDPServer;
import com.minhui.vpn.VPNConstants;
import com.minhui.vpn.VPNLog;
import com.minhui.vpn.nat.NatSession;
import com.minhui.vpn.nat.NatSessionManager;
import com.minhui.vpn.processparse.PortHostService;
import com.minhui.vpn.utils.ACache;
import com.minhui.vpn.utils.SocketUtils;
import com.minhui.vpn.utils.TcpDataSaveHelper;
import com.minhui.vpn.utils.ThreadProxy;
import com.minhui.vpn.utils.TimeFormatUtil;
import com.minhui.vpn.utils.VpnServiceHelper;

import java.io.File;
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

public class UDPTunnel implements KeyHandler {


    private static final String TAG = UDPTunnel.class.getSimpleName();
    private final VpnService vpnService;
    private final Selector selector;
    private final UDPServer vpnServer;
    private final Queue<Packet> outputQueue;
    private TcpDataSaveHelper helper;
    private Packet referencePacket;
    private SelectionKey selectionKey;

    private DatagramChannel channel;
    private final ConcurrentLinkedQueue<Packet> toNetWorkPackets = new ConcurrentLinkedQueue<>();
    private static final int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.UDP_HEADER_SIZE;
    private Short portKey;
    String ipAndPort;
    private final NatSession session;
    private final Handler handler;

    public UDPTunnel(VpnService vpnService, Selector selector, UDPServer vpnServer, Packet packet, Queue<Packet> outputQueue, short portKey) {
        this.vpnService = vpnService;
        this.selector = selector;
        this.vpnServer = vpnServer;
        this.referencePacket = packet;
        ipAndPort = packet.getIpAndPort();
        this.outputQueue = outputQueue;
        this.portKey = portKey;
        session = NatSessionManager.getSession(portKey);
        handler = new Handler(Looper.getMainLooper());

        if (VpnServiceHelper.isUDPDataNeedSave()) {
            String helperDir = new StringBuilder()
                    .append(VPNConstants.DATA_DIR)
                    .append(TimeFormatUtil.formatYYMMDDHHMMSS(session.vpnStartTime))
                    .append("/")
                    .append(session.getUniqueName())
                    .toString();
            helper = new TcpDataSaveHelper(helperDir);
        }

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
            session.receivePacketNum++;
            session.receiveByteNum += readBytes;
            session.lastRefreshTime = System.currentTimeMillis();

            if (VpnServiceHelper.isUDPDataNeedSave() && helper != null) {
                saveData(receiveBuffer.array(), readBytes, false);
            }

        }
    }

    private void saveData(byte[] array, int saveSize, boolean isRequest) {
        TcpDataSaveHelper.SaveData saveData = new TcpDataSaveHelper
                .SaveData
                .Builder()
                .offSet(HEADER_SIZE)
                .length(saveSize)
                .needParseData(array)
                .isRequest(isRequest)
                .build();
        helper.addData(saveData);
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
            session.packetSent++;
            int sendSize = payloadBuffer.limit() - payloadBuffer.position();
            session.bytesSent += sendSize;
            if (VpnServiceHelper.isUDPDataNeedSave() && helper != null) {
                saveData(payloadBuffer.array(), sendSize, true);
            }
            session.lastRefreshTime = System.currentTimeMillis();
            while (payloadBuffer.hasRemaining()) {
                channel.write(payloadBuffer);
            }


        } catch (IOException e) {
            VPNLog.w(TAG, "Network write error: " + ipAndPort, e);
            vpnServer.closeUDPConn(this);
        }
    }

    public void initConnection() {
        VPNLog.d(TAG, "init  ipAndPort:" + ipAndPort);
        InetAddress destinationAddress = referencePacket.ip4Header.destinationAddress;
        int destinationPort = referencePacket.udpHeader.destinationPort;
        try {
            channel = DatagramChannel.open();
            vpnService.protect(channel.socket());
            channel.configureBlocking(false);
            channel.connect(new InetSocketAddress(destinationAddress, destinationPort));
            selector.wakeup();
            selectionKey = channel.register(selector,
                    SelectionKey.OP_READ, this);
        } catch (IOException e) {
            SocketUtils.closeResources(channel);
            return;
        }
        referencePacket.swapSourceAndDestination();
        addToNetWorkPacket(referencePacket);
    }

    public void processPacket(Packet packet) {
        addToNetWorkPacket(packet);
        updateInterests();
    }

    public void close() {
        try {
            if (selectionKey != null) {
                selectionKey.cancel();
            }
            if (channel != null) {
                channel.close();
            }
            if (session.appInfo == null && PortHostService.getInstance() != null) {
                PortHostService.getInstance().refreshSessionInfo();
            }
            //需要延迟一秒在保存 等到app信息完全刷新
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    ThreadProxy.getInstance().execute(new Runnable() {
                        @Override
                        public void run() {
                            if (session.receiveByteNum == 0 && session.bytesSent == 0) {
                                return;
                            }

                            String configFileDir = VPNConstants.CONFIG_DIR
                                    + TimeFormatUtil.formatYYMMDDHHMMSS(session.vpnStartTime);
                            File parentFile = new File(configFileDir);
                            if (!parentFile.exists()) {
                                parentFile.mkdirs();
                            }
                            //说已经存了
                            File file = new File(parentFile, session.getUniqueName());
                            if (file.exists()) {
                                return;
                            }
                            ACache configACache = ACache.get(parentFile);
                            configACache.put(session.getUniqueName(), session);
                        }
                    });
                }
            }, 1000);
        } catch (Exception e) {
            VPNLog.w(TAG, "error to close UDP channel IpAndPort" + ipAndPort + ",error is " + e.getMessage());
        }

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


    @Override
    public void onKeyReady(SelectionKey key) {
        processKey(key);
    }

    public Short getPortKey() {
        return portKey;
    }
}
