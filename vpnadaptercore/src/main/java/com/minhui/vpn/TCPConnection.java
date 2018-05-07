package com.minhui.vpn;
/**
 * Created by minhui.zhu on 2017/6/24.
 * Copyright © 2017年 minhui.zhu. All rights reserved.
 */

import android.net.VpnService;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Transmission Control Block
 */
public class TCPConnection extends BaseNetConnection implements Serializable {
    private static final String TAG = TCPConnection.class.getSimpleName();
    private static final int VALID_TCP_DATA_SIZE = 10;
    private final VpnService vpnService;
    private final Selector selector;
    private final VPNServer vpnServer;
    private final ConcurrentLinkedQueue<Packet> outputQueue;
    private final Object keyLock = new Object();
    private static final int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.TCP_HEADER_SIZE;

    long mySequenceNum;
    private long theirSequenceNum;
    long myAcknowledgementNum;
    private long theirAcknowledgementNum;
    private TCPStatus status;
    long buildTime;

    private final HashMap<Long, Packet> toDevicePacket = new HashMap<>();
    long startCloseTime;
    private final HashMap<Long, Packet> toNetWorkPacket = new HashMap<>();
    private int clientWindow;
    private static final int TIME_OUT = 10000;

    private Packet referencePacket;

    private SocketChannel channel;
    private boolean waitingForNetworkData;
    private SelectionKey selectionKey;
    private final VPNServer.KeyHandler keyHandler;
    private int interestingOps;
    private int remainingWindow;
    private String requestData;

    private boolean hasWriteConnConfig = false;

    private final TcpDataSaveHelper tcpDataSaveHelper;

    TCPConnection(VpnService vpnService, Selector selector, VPNServer vpnServer, Packet packet, ConcurrentLinkedQueue<Packet> outputQueue) {
        super();
        this.vpnService = vpnService;
        this.selector = selector;
        this.vpnServer = vpnServer;
        referencePacket = packet;
        ipAndPort = packet.getIpAndPort();
        this.outputQueue = outputQueue;
        mySequenceNum = SocketUtils.getRandomSequence();
        theirSequenceNum = packet.tcpHeader.sequenceNumber;
        theirAcknowledgementNum = mySequenceNum;
        myAcknowledgementNum = theirSequenceNum + 1;
        clientWindow = packet.tcpHeader.getWindow();
        keyHandler = new VPNServer.KeyHandler() {
            @Override
            public void onKeyReady(SelectionKey key) {
                processKey(key);
            }
        };
        port = packet.tcpHeader.sourcePort;
        type = TCP;
        vpnStartTime = LocalVPNService.getInstance().getVpnStartTime();
        String formatVpnStartTime = TimeFormatUtil.formatYYMMDDHHMMSS(vpnStartTime);
        tcpDataSaveHelper = new TcpDataSaveHelper(VPNConstants.DATA_DIR + formatVpnStartTime + "/" + getUniqueName());
    }


    private void processKey(SelectionKey key) {
        if (key.isValid()) {
            Log.d(TAG, "process key ip:" + ipAndPort);
            if (key.isConnectable()) {
                processConnect();
                updateKey();
            } else if (key.isReadable()) {
                processReadFromNet();
                updateKey();
            } else if (key.isWritable()) {
                processWriteToNet();
                updateKey();
            }
        }


    }


    public ArrayList<ConversationData> getConversation() {
        ArrayList<ConversationData> conversationData = new ArrayList<>();
        conversationData.addAll(conversation);
        return conversationData;
    }

    private void processWriteToNet() {
        if (getToNetPacketNum() == 0) {
            return;
        }


        try {
            Packet toNetPacket = getToNetPacket(myAcknowledgementNum);
            if (toNetPacket != null) {
                ByteBuffer payloadBuffer = toNetPacket.backingBuffer;
                int playLoadSize = payloadBuffer.limit() - payloadBuffer.position();

                saveConversationData(payloadBuffer, playLoadSize, true);
                if (sendByteNum == 0 && playLoadSize > VALID_TCP_DATA_SIZE) {
                    toNetPacket.parseHttpRequestHeader();
                    hostName = toNetPacket.getHostName();
                    isSSL = toNetPacket.isSSL();
                    url = toNetPacket.getRequestUrl();
                }
                if (!isSSL && hostName == null && playLoadSize > VALID_TCP_DATA_SIZE) {
                    hostName = toNetPacket.parseAndGetHostName();
                }
                while (payloadBuffer.hasRemaining()) {
                    channel.write(payloadBuffer);
                }
                VPNConnectManager.getInstance().addSendNum(toNetPacket, playLoadSize);

                sendByteNum = sendByteNum + playLoadSize;
                sendPacketNum++;
                refreshTime = System.currentTimeMillis();
                //after write to the channel we should notify local socket

                Packet packet = referencePacket.duplicated();
                ByteBuffer responseBuffer = SocketUtils.getByteBuffer();
                Packet.TCPHeader tcpHeader = toNetPacket.tcpHeader;
                myAcknowledgementNum = tcpHeader.sequenceNumber + playLoadSize;
                releaseToNetPacket();
                packet.updateTCPBuffer(responseBuffer, (byte) (Packet.TCPHeader.ACK | Packet.TCPHeader.PSH), mySequenceNum, myAcknowledgementNum, 0);
                outputQueue.offer(packet);

                Log.d(TAG, "WriteToNet valid" + " myAck:" + myAcknowledgementNum + "size:  "
                        + playLoadSize + "ToNum" + getToNetPacketNum() + ipAndPort);

            } else {
                Log.d(TAG, "WriteToNet invalid" + " myAck:" + myAcknowledgementNum + "toNet size" + toNetWorkPacket.size() + ipAndPort);
                releaseToNetPacket();
            }

        } catch (Exception e) {
            Log.w(TAG, "processWriteToNet error: " + ipAndPort, e);
            ByteBuffer responseBuffer = SocketUtils.getByteBuffer();
            Packet packet = referencePacket.duplicated();
            packet.updateTCPBuffer(responseBuffer, (byte) Packet.TCPHeader.RST, 0, myAcknowledgementNum, 0);
            outputQueue.offer(packet);
            vpnServer.closeTCPConnection(this);
        }
    }

    private void saveConversationData(ByteBuffer payloadBuffer, int playLoadSize, boolean isRequest) {
        TcpDataSaveHelper.SaveData saveData = new TcpDataSaveHelper
                .SaveData.Builder()
                .isRequest(isRequest)
                .length(playLoadSize)
                .needParseData(payloadBuffer.array())
                .offSet(HEADER_SIZE)
                .build();
        tcpDataSaveHelper.addData(saveData);
    }


    private void processReadFromNet() {
        Log.d(TAG, "FromNet ipAndPort:" + ipAndPort);
        ByteBuffer receiveBuffer = SocketUtils.getByteBuffer();
        // Leave space for the header
        receiveBuffer.position(HEADER_SIZE);
        int remainingClientWindow = getRemainingClientWindow();
        if (remainingClientWindow <= 0) {
            Log.d(TAG, "FromNet has no window");
            return;
        }
        int maxPayloadSize = Math.min(remainingClientWindow, VPNConstants.MAX_PAYLOAD_SIZE);
        receiveBuffer.limit(HEADER_SIZE + maxPayloadSize);

        Packet packet = referencePacket.duplicated();
        int readBytes;
        try {
            readBytes = channel.read(receiveBuffer);
        } catch (IOException e) {
            Log.w(TAG, "Network read error: " + ipAndPort, e);
            packet.updateTCPBuffer(receiveBuffer, (byte) Packet.TCPHeader.RST, 0, myAcknowledgementNum, 0);
            outputQueue.offer(packet);
            vpnServer.closeTCPConnection(this);
            return;
        }
        packet.releaseAfterWritingToDevice = false;
        if (readBytes == -1) {
            Log.d(TAG, "processReadFromNet  CLOSE_WAIT  " + ipAndPort);
            waitingForNetworkData = false;
            //本地socket已经发了fin指令，既本地socket到net的channel已关闭，现在收到服务器的fin指令，要将服务器到本地的channel关闭
            if (TCPStatus.CLOSE_WAIT == status) {
                setStatus(TCPStatus.LAST_ACK);
            } else {
                setStatus(TCPStatus.CLOSE_WAIT);
            }
            packet.updateTCPBuffer(receiveBuffer, (byte) (Packet.TCPHeader.FIN | Packet.TCPHeader.ACK), mySequenceNum
                    , myAcknowledgementNum, 0);
            putToDevicePacket(mySequenceNum, packet);
            mySequenceNum++;

        } else if (readBytes == 0) {
            Log.d(TAG, "processReadFromNet  no data   " + ipAndPort);
            return;
        } else {

            packet.updateTCPBuffer(receiveBuffer, (byte) (Packet.TCPHeader.PSH | Packet.TCPHeader.ACK),
                    mySequenceNum, myAcknowledgementNum, readBytes);
            Log.d(TAG, "FromNet  mySeq:" + mySequenceNum +
                    "size:" + readBytes + referencePacket.ip4Header.sourceAddress +
                    referencePacket.tcpHeader.destinationPort);
            putToDevicePacket(mySequenceNum, packet);
            // Next sequence number
            mySequenceNum += readBytes;
            receiveBuffer.position(HEADER_SIZE + readBytes);
            VPNConnectManager.getInstance().addReceiveNum(packet, readBytes);
            receiveByteNum = receiveByteNum + readBytes;
            receivePacketNum++;
            refreshTime = System.currentTimeMillis();
            saveConversationData(packet.backingBuffer, readBytes, false);
        }

        outputQueue.offer(packet);
    }

    private void processConnect() {
        Packet packet = referencePacket.duplicated();
        try {
            if (channel.finishConnect()) {
                setStatus(TCPStatus.SYN_RECEIVED);
                ByteBuffer responseBuffer = SocketUtils.getByteBuffer();
                packet.updateTCPBuffer(responseBuffer, (byte) (Packet.TCPHeader.SYN | Packet.TCPHeader.ACK),
                        mySequenceNum, myAcknowledgementNum, 0);
                outputQueue.offer(packet);
                mySequenceNum++; // SYN counts as a byte
                Log.d(TAG, "processConnect " + ipAndPort);
            } else {
                Log.d(TAG, "processConnect not finish" + ipAndPort);
            }
        } catch (Exception e) {
            Log.w(TAG, "Connection error: " + ipAndPort, e);
            ByteBuffer responseBuffer = SocketUtils.getByteBuffer();
            packet.updateTCPBuffer(responseBuffer, (byte) Packet.TCPHeader.RST, 0, myAcknowledgementNum, 0);
            outputQueue.offer(packet);
            vpnServer.closeTCPConnection(this);
        }
    }

    void processSendPacket(Packet packet) {
        Packet.TCPHeader tcpHeader = packet.tcpHeader;
        if (tcpHeader.isSYN()) {
            processSendDuplicateSYN(packet);
        } else if (tcpHeader.isRST()) {
            processSendRST(packet);
        } else if (tcpHeader.isFIN()) {
            processSendFIN(packet);
        } else if (tcpHeader.isACK()) {
            processSendACK(packet);
        }
    }

    private void processSendACK(Packet currentPacket) {
        Packet.TCPHeader tcpHeader = currentPacket.tcpHeader;


        if (theirAcknowledgementNum < tcpHeader.acknowledgementNumber) {
            theirAcknowledgementNum = tcpHeader.acknowledgementNumber;
            clientWindow = tcpHeader.window;
        }

        if (TCPStatus.SYN_RECEIVED == status) {
            Log.d(TAG, "processSendACK  SYN_RECEIVED " + ipAndPort);
            setStatus(TCPStatus.ESTABLISHED);
            waitingForNetworkData = true;
            return;
        } else if (TCPStatus.LAST_ACK == status) {
            Log.d(TAG, "processSendACK  LAST_ACK " + ipAndPort);
            vpnServer.closeTCPConnection(this);
            return;
        }
        Log.d(TAG, "SendACK size " + currentPacket.playLoadSize + "toDevice:" + getToDevicePacketSize() +
                "ToNet:" + getToNetPacketNum() + ipAndPort);
        processResendPacket(tcpHeader.acknowledgementNumber);

        //只是本地socket接收到数据的回应，不需要再重复回应

        if (currentPacket.playLoadSize == 0) {
            updateKey();
            return;
        }
        addToNetPacket(tcpHeader.sequenceNumber, currentPacket);
        updateKey();
    }

    private void processResendPacket(long acknowledgement) {

        releaseToDevicePacket(acknowledgement);
        if (acknowledgement == mySequenceNum) {
            return;
        }
        Integer time = getNotifyTime(acknowledgement);
        if (time == null) {
            time = 1;
        } else {
            time++;
        }
        if (time <= 4) {
            putNotifyTime(acknowledgement, time);
            return;
        }
        Log.d(TAG, "not get all data TCPConnection.mySequenceNum " + ipAndPort);
        Packet preferPacket = getToDevicePacket(acknowledgement);
        if (preferPacket != null) {
            Log.d(TAG, "write again  true" + ipAndPort + "Size" + preferPacket.playLoadSize + "limit" +
                    preferPacket.backingBuffer.limit() + "position" + preferPacket.backingBuffer.position());
            outputQueue.offer(preferPacket);
            putNotifyTime(acknowledgement, 0);
            return;
        }
        preferPacket = getClosestPacket(toDevicePacket, acknowledgement);
        //说明
        if (preferPacket != null) {

            outputQueue.offer(preferPacket);
            putNotifyTime(acknowledgement, 0);
            Log.d(TAG, "write again closest" + ipAndPort + "Size" + preferPacket.playLoadSize + "limit" +
                    preferPacket.backingBuffer.limit() + "position" + preferPacket.backingBuffer.position());
        }


    }

    private void processSendFIN(Packet sendPacket) {
        myAcknowledgementNum = sendPacket.tcpHeader.sequenceNumber + 1;
        theirAcknowledgementNum = sendPacket.tcpHeader.acknowledgementNumber;
        clientWindow = sendPacket.tcpHeader.getWindow();
        if (waitingForNetworkData) {
            setStatus(TCPStatus.CLOSE_WAIT);
        } else {
            setStatus(TCPStatus.LAST_ACK);
            vpnServer.closeTCPConnection(this);
        }
        Packet packet = referencePacket.duplicated();
        ByteBuffer responseBuffer = SocketUtils.getByteBuffer();
        packet.updateTCPBuffer(responseBuffer, (byte) Packet.TCPHeader.ACK,
                mySequenceNum, myAcknowledgementNum, 0);
    }

    private void processSendRST(Packet packet) {
        vpnServer.closeTCPConnection(this);
    }


    private void processSendDuplicateSYN(Packet sendPacket) {
        if (TCPStatus.SYN_SENT == status) {
            Log.d(TAG, "processSendDuplicateSYN SYN_SENT" + ipAndPort);
            myAcknowledgementNum = sendPacket.tcpHeader.sequenceNumber + 1;
            return;
        } else {
            Log.d(TAG, "processSendDuplicateSYN not SYN_SENT" + ipAndPort);
        }
        responseRST();

    }

    private void responseRST() {
        Packet responsePacket = referencePacket.duplicated();
        ByteBuffer responseBuffer = SocketUtils.getByteBuffer();
        responsePacket.updateTCPBuffer(responseBuffer, (byte) Packet.TCPHeader.RST, 0, myAcknowledgementNum + 1, 0);
        outputQueue.offer(responsePacket);
        vpnServer.closeTCPConnection(this);
    }

    void initConnection() {
        InetAddress destinationAddress = referencePacket.ip4Header.destinationAddress;
        int destinationPort = referencePacket.tcpHeader.destinationPort;
        referencePacket.swapSourceAndDestination();
        Packet packet = referencePacket.duplicated();
        ByteBuffer responseBuffer = SocketUtils.getByteBuffer();
        try {
            channel = SocketChannel.open();
            Socket socket = channel.socket();
            Log.i(TAG, "bind Socket result is :" + "ipAndPort is " + ipAndPort);
            channel.configureBlocking(false);
            vpnService.protect(socket);
            Log.d(TAG, "init conn  :" + ipAndPort + "  init mySequenceNum  " + mySequenceNum);

            channel.connect(new InetSocketAddress(destinationAddress, destinationPort));
            selector.wakeup();
            selectionKey = channel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ, ipAndPort);
            if (channel.finishConnect()) {
                setStatus(TCPStatus.SYN_RECEIVED);
                packet.updateTCPBuffer(responseBuffer, (byte) (Packet.TCPHeader.SYN | Packet.TCPHeader.ACK),
                        mySequenceNum, myAcknowledgementNum, 0);
                mySequenceNum++; // SYN counts as a byte
                Log.d(TAG, "init finish:" + ipAndPort + "  mySequenceNum  " + mySequenceNum);
            } else {
                setStatus(TCPStatus.SYN_SENT);
                Log.d(TAG, "init  SYN_SENT:" + ipAndPort);
                return;
            }
        } catch (Exception e) {
            Log.w(TAG, "init  Connection error: " + ipAndPort, e);
            packet.updateTCPBuffer(responseBuffer, (byte) Packet.TCPHeader.RST, 0, myAcknowledgementNum, 0);
            vpnServer.closeTCPConnection(this);
        }
        outputQueue.offer(packet);
    }

    Integer getNotifyTime(Long acknowledgementNumber) {
        return tcpAcknowledgementTimes.get(acknowledgementNumber);
    }

    void putNotifyTime(Long acknowledgementTime, Integer time) {
        tcpAcknowledgementTimes.put(acknowledgementTime, time);
    }

    private Map<Long, Integer> tcpAcknowledgementTimes = new HashMap<>();

    void setStatus(TCPStatus status) {
        if (status == null) {
            return;
        }
        this.status = status;
        if (TCPStatus.LAST_ACK == status || TCPStatus.CLOSE_WAIT == status) {
            startCloseTime = SystemClock.currentThreadTimeMillis();
        }
    }

    int getRemainingClientWindow() {
        // in Java, (signed) integer overflow is well-defined: it wraps around
        remainingWindow = (int) (theirAcknowledgementNum + clientWindow - mySequenceNum);
        if (remainingWindow < 0 || remainingWindow > clientWindow) {
            // our sequence number is outside their window
            return 0;
        }
        return remainingWindow;
    }

    TCPStatus getStatus() {
        return status;
    }


    Packet getClosestPacket(Map<Long, Packet> packets, long acknowledgementNumber) {


        Iterator<Map.Entry<Long, Packet>> iterator = packets.entrySet().iterator();
        Packet writePacket = null;
        //找到不完全的包
        while (iterator.hasNext()) {
            Map.Entry<Long, Packet> next = iterator.next();
            Long nextSequence = next.getKey();
            Packet nextPacket = next.getValue();
            if (nextSequence < acknowledgementNumber && nextSequence + nextPacket.playLoadSize > acknowledgementNumber) {
                writePacket = nextPacket;
                break;
            }
        }
        return writePacket;
    }

    void addToNetPacket(Long sequenceNum, Packet packet) {
        synchronized (toNetWorkPacket) {
            toNetWorkPacket.put(sequenceNum, packet);
        }
    }

    Packet getToNetPacket(Long myAcknowledgementNum) {
        synchronized (toNetWorkPacket) {
            Packet packet = toNetWorkPacket.get(myAcknowledgementNum);
            if (packet != null) {
                return packet;
            }
            return getClosestPacket(toNetWorkPacket, myAcknowledgementNum);
        }
    }

    int getToNetPacketNum() {
        return toNetWorkPacket.size();
    }


    void releaseToNetPacket() {
        synchronized (toNetWorkPacket) {
            Iterator<Map.Entry<Long, Packet>> iterator = toNetWorkPacket.entrySet().iterator();
            while ((iterator.hasNext())) {
                Map.Entry<Long, Packet> next = iterator.next();
                Long sequenceNum = next.getKey();
                Packet packet = next.getValue();
                if (sequenceNum + packet.playLoadSize <= myAcknowledgementNum) {
                    iterator.remove();
                }
            }
        }
    }

    String getIpAndPort() {
        return ipAndPort;
    }


    void putToDevicePacket(Long sequenceNum, Packet packet) {
        synchronized (toDevicePacket) {
            toDevicePacket.put(sequenceNum, packet);
        }
    }


    Packet getToDevicePacket(Long sequenceNum) {
        synchronized (toDevicePacket) {
            return toDevicePacket.get(sequenceNum);
        }

    }

    int getToDevicePacketSize() {
        synchronized (toDevicePacket) {
            return toDevicePacket.size();
        }
    }

    void releaseToDevicePacket(Long acknowledge) {
        synchronized (toDevicePacket) {
            Iterator<Map.Entry<Long, Packet>> iterator = toDevicePacket.entrySet().iterator();
            while ((iterator.hasNext())) {
                Map.Entry<Long, Packet> next = iterator.next();
                Long sequenceNum = next.getKey();
                Packet packet = next.getValue();
                if (sequenceNum + packet.playLoadSize < acknowledge) {
                    packet.backingBuffer = null;
                    packet.cancelSending = true;
                    iterator.remove();
                }
            }
        }
        Iterator<Map.Entry<Long, Integer>> acknowledgeIterator = tcpAcknowledgementTimes.entrySet().iterator();
        while (acknowledgeIterator.hasNext()) {
            Map.Entry<Long, Integer> next = acknowledgeIterator.next();
            if (next.getKey() < acknowledge) {
                acknowledgeIterator.remove();
            }
        }
    }


    static boolean isInClosingStatus(TCPStatus status) {
        return TCPStatus.LAST_ACK == status;
    }

    static boolean isInConnectingStatus(TCPStatus status) {
        return TCPStatus.SYN_SENT == status || TCPStatus.SYN_RECEIVED == status;
    }


    void closeChannelAndClearCache() {
        try {
            selectionKey.cancel();
            channel.close();
            status = TCPStatus.LAST_ACK;
            Log.d(TAG, "closeChannelAndClearCache ipAndPort" + ipAndPort);
            Iterator<Map.Entry<Long, Packet>> iterator = toDevicePacket.entrySet().iterator();
            while ((iterator.hasNext())) {
                Packet packet = iterator.next().getValue();
                packet.cancelSending = true;
                iterator.remove();
            }
            Iterator<Map.Entry<Long, Packet>> toNetPacketIterator = toNetWorkPacket.entrySet().iterator();
            while ((toNetPacketIterator.hasNext())) {
                iterator.remove();
            }
            saveConfigData();
        } catch (Exception e) {
            Log.d(TAG, "closeChannelAndClearCache failed e" + e.getStackTrace());
        }
    }

    private void saveConfigData() {
        ThreadProxy.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (receiveByteNum == 0 && sendByteNum == 0) {
                        return;
                    }
                    String configFileDir = VPNConstants.CONFIG_DIR
                            + TimeFormatUtil.formatYYMMDDHHMMSS(vpnStartTime);
                    File parentFile = new File(configFileDir);
                    if (!parentFile.exists()) {
                        parentFile.mkdirs();
                    }
                    //说已经存了
                    File file = new File(parentFile, getUniqueName());
                    if (file.exists()) {
                        return;
                    }
                    ACache configACache = ACache.get(parentFile);
                    BaseNetConnection baseConnection = new BaseNetConnection(TCPConnection.this);
                    configACache.put(getUniqueName(), baseConnection);

                } catch (Exception e) {
                    VPNLog.e(TAG, "failed to saveConfigData ");
                }
            }
        });

    }

    private void updateKey() {
        synchronized (keyLock) {
            if (!selectionKey.isValid()) {
                Log.d(TAG, "updateKey Key is no Valid  " + ipAndPort);
                return;
            }
            int newInterestingOps = 0;
            if (mayRead()) {
                newInterestingOps |= SelectionKey.OP_READ;  //1
            }
            if (mayWrite()) {
                newInterestingOps |= SelectionKey.OP_WRITE;//4
            }
            if (mayConnect()) {
                newInterestingOps |= SelectionKey.OP_CONNECT;//8
            }
            selector.wakeup();
            selectionKey.interestOps(newInterestingOps);
            if (interestingOps != newInterestingOps) {
                interestingOps = newInterestingOps;
                Log.d(TAG, "updateKey Ops：" + interestingOps + "win:" + remainingWindow + ipAndPort);
            } else {
                Log.d(TAG, "no updateKey Ops：" + interestingOps + "win:" + remainingWindow + ipAndPort);
            }

        }

    }

    private boolean mayWrite() {
        return getToNetPacketNum() != 0;
    }

    private boolean mayRead() {
        return waitingForNetworkData && getRemainingClientWindow() > 0;
    }


    private boolean mayConnect() {
        return TCPStatus.SYN_SENT == getStatus();
    }

    VPNServer.KeyHandler getKeyHandler() {
        return keyHandler;
    }


    // TCP has more states, but we need only these
    enum TCPStatus {
        SYN_SENT,
        SYN_RECEIVED,
        ESTABLISHED,
        CLOSE_WAIT,
        LAST_ACK,
    }
}
