package com.zhixin.roav.vpnadaptercore;


import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Selector;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by minhui.zhu on 2017/8/21.
 * Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class VPNClient implements CloseableRun {

    private final ConcurrentLinkedQueue<Packet> networkToDeviceQueue;
    private final VPNServer vpnServer;
    private FileDescriptor vpnFileDescriptor;
    private boolean isClose;
    private Selector selector;
    private static final String TAG = VPNClient.class.getSimpleName();

    VPNClient(FileDescriptor vpnFileDescriptor, VPNServer vpnServer,
              ConcurrentLinkedQueue<Packet> networkToDeviceQueue, Selector selector
    ) {
        this.vpnFileDescriptor = vpnFileDescriptor;
        this.networkToDeviceQueue = networkToDeviceQueue;
        this.selector = selector;
        this.vpnServer = vpnServer;
    }

    @Override
    public void run() {
        VPNLog.i(TAG, "Started");
        FileChannel vpnInput = new FileInputStream(vpnFileDescriptor).getChannel();
        FileChannel vpnOutput = new FileOutputStream(vpnFileDescriptor).getChannel();

        ByteBuffer bufferToNetwork = null;
        boolean dataSent = true;
        while (true) {
            try {
                if (isRunClose()) {
                    break;
                }
                if (dataSent)
                    bufferToNetwork = ByteBuffer.allocate(VPNConstants.BUFFER_SIZE);
                else
                    bufferToNetwork.clear();


                int readBytes = vpnInput.read(bufferToNetwork);
                if (readBytes > 0) {
                    dataSent = true;
                    bufferToNetwork.flip();
                    Packet packet = new Packet(bufferToNetwork);
                    if (packet.isUDP()) {
                        VPNLog.d(TAG, "to net isUDP ipport:" + packet.ip4Header.destinationAddress.getHostAddress());
                    } else if (packet.isTCP()) {
                        VPNLog.d(TAG, "" +
                                "" +
                                "to net isTCP " + "sequenceNumber" + packet.tcpHeader.sequenceNumber +
                                "playLoadSize" + packet.playLoadSize + "ip port" +
                                packet.ip4Header.destinationAddress.getHostAddress() +
                                ":" + packet.tcpHeader.destinationPort + ";" + packet.tcpHeader.sourcePort);
                    } else {
                        VPNLog.w(TAG, "Unknown packet type " + packet.ip4Header.destinationAddress.getHostAddress());
                        dataSent = false;
                    }
                    vpnServer.processPacket(packet);

                } else {
                    dataSent = false;
                }
                Packet packet = networkToDeviceQueue.poll();
                boolean dataRecevied = false;
                if (packet != null && !packet.cancelSending) {
                    ByteBuffer bufferFromNetwork = packet.backingBuffer;
                    bufferFromNetwork.flip();
                    if (packet.isTCP()) {
                        VPNLog.d(TAG, "tcp to device seq:" + packet.tcpHeader.sequenceNumber +
                                "aknow:" + packet.tcpHeader.acknowledgementNumber +
                                "playSize:" + packet.playLoadSize + "limit:" + packet.backingBuffer.limit()
                                + "ip:" + packet.ip4Header.sourceAddress.getHostAddress() +
                                ":" + packet.tcpHeader.sourcePort + ";" + packet.tcpHeader.destinationPort);
                    } else {
                        VPNLog.d(TAG, "udp to device playSize:" + packet.playLoadSize +
                                "limit:" + packet.backingBuffer.limit()
                                + "ip:" + packet.ip4Header.sourceAddress.getHostAddress() +
                                ":" + packet.udpHeader.sourcePort + ";" + packet.udpHeader.destinationPort);
                    }
                    while (bufferFromNetwork.hasRemaining())
                        vpnOutput.write(bufferFromNetwork);
                    if (packet.releaseAfterWritingToDevice) {
                        packet.backingBuffer = null;
                    }
                    dataRecevied = true;
                }
                if (!dataSent && !dataRecevied) {
                    Thread.sleep(5);
                }
            } catch (Exception e) {
                VPNLog.w(TAG, e.toString(), e);
            }
        }
        SocketUtils.closeResources(vpnInput, vpnOutput);
        SocketUtils.closeResources(selector);
    }

    @Override
    public void closeRun() {
        isClose = true;
    }

    @Override
    public boolean isRunClose() {
        return isClose;
    }
}
