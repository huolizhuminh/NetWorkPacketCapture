package com.minhui.vpn;
/**
 * Created by minhui.zhu on 2017/6/24.
 * Copyright © 2017年 minhui.zhu. All rights reserved.
 */

import android.text.TextUtils;
import android.util.Log;

import com.minhui.vpn.utils.CommonMethods;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Representation of an IP Packet
 */

public class Packet implements Serializable {
    public static final int IP4_HEADER_SIZE = 20;
    public static final int TCP_HEADER_SIZE = 20;
    public static final int UDP_HEADER_SIZE = 8;
    private static final int FIRST_TCP_DATA = 40;
    private static final String TAG = "Packet";


    public IP4Header ip4Header;
    public TCPHeader tcpHeader;
    public UDPHeader udpHeader;
    public ByteBuffer backingBuffer;

    private boolean isTCP;
    private boolean isUDP;
    boolean releaseAfterWritingToDevice = true;
    boolean cancelSending = false;
    public int playLoadSize = 0;
    private boolean isSSL;
    private String hostName;
    private String method;
    private String requestUrl;
    private boolean cannotParse;
    private boolean isHttp;
    private String urlPath;


    public boolean isSSL() {
        return isSSL;
    }

    public String getHostName() {
        return hostName;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public IP4Header getIp4Header() {
        return ip4Header;
    }

    public TCPHeader getTcpHeader() {
        return tcpHeader;
    }

    public UDPHeader getUdpHeader() {
        return udpHeader;
    }

    public ByteBuffer getBackingBuffer() {
        return backingBuffer;
    }

    public boolean isReleaseAfterWritingToDevice() {
        return releaseAfterWritingToDevice;
    }

    public boolean isCancelSending() {
        return cancelSending;
    }

    public int getPlayLoadSize() {
        return playLoadSize;
    }

    public Packet(ByteBuffer buffer) throws UnknownHostException {
        this.ip4Header = new IP4Header(buffer);
        if (this.ip4Header.protocol == IP4Header.TransportProtocol.TCP) {
            this.tcpHeader = new TCPHeader(buffer);
            this.isTCP = true;
        } else if (ip4Header.protocol == IP4Header.TransportProtocol.UDP) {
            this.udpHeader = new UDPHeader(buffer);
            this.isUDP = true;
        }
        this.backingBuffer = buffer;
        this.playLoadSize = buffer.limit() - buffer.position();
    }

    private Packet() {

    }

    public Packet duplicated() {
        Packet packet = new Packet();
        packet.ip4Header = ip4Header.duplicate();
        if (tcpHeader != null) {
            packet.tcpHeader = tcpHeader.duplicate();
        }
        if (udpHeader != null) {
            packet.udpHeader = udpHeader.duplicate();
        }
        packet.isTCP = isTCP;
        packet.isUDP = isUDP;
        return packet;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Packet{");
        sb.append("ip4Header=").append(ip4Header);
        if (isTCP) {
            sb.append(", tcpHeader=").append(tcpHeader);
        } else if (isUDP) {
            sb.append(", udpHeader=").append(udpHeader);
        }
        sb.append(", payloadSize=").append(backingBuffer.limit() - backingBuffer.position());
        sb.append('}');
        return sb.toString();
    }

    public boolean isTCP() {
        return isTCP;
    }

    boolean isUDP() {
        return isUDP;
    }

    public void swapSourceAndDestination() {
        InetAddress newSourceAddress = ip4Header.destinationAddress;
        ip4Header.destinationAddress = ip4Header.sourceAddress;
        ip4Header.sourceAddress = newSourceAddress;

        if (isUDP) {
            int newSourcePort = udpHeader.destinationPort;
            udpHeader.destinationPort = udpHeader.sourcePort;
            udpHeader.sourcePort = newSourcePort;
        } else if (isTCP) {
            int newSourcePort = tcpHeader.destinationPort;
            tcpHeader.destinationPort = tcpHeader.sourcePort;
            tcpHeader.sourcePort = newSourcePort;
        }
    }

    void updateTCPBuffer(ByteBuffer buffer, byte flags, long sequenceNum, long ackNum, int payloadSize) {
        buffer.position(0);
        fillHeader(buffer);
        backingBuffer = buffer;

        tcpHeader.flags = flags;
        backingBuffer.put(IP4_HEADER_SIZE + 13, flags);

        tcpHeader.sequenceNumber = sequenceNum;
        backingBuffer.putInt(IP4_HEADER_SIZE + 4, (int) sequenceNum);

        tcpHeader.acknowledgementNumber = ackNum;
        backingBuffer.putInt(IP4_HEADER_SIZE + 8, (int) ackNum);

        // Reset header size, since we don't need options
        byte dataOffset = (byte) (TCP_HEADER_SIZE << 2);
        tcpHeader.dataOffsetAndReserved = dataOffset;
        backingBuffer.put(IP4_HEADER_SIZE + 12, dataOffset);

        int tcpCheckSum = updateTCPChecksum(payloadSize);
        int ip4TotalLength = IP4_HEADER_SIZE + TCP_HEADER_SIZE + payloadSize;
        backingBuffer.putShort(2, (short) ip4TotalLength);
        ip4Header.totalLength = ip4TotalLength;

        updateIP4Checksum();
        this.playLoadSize = payloadSize;
    }

    public void updateUDPBuffer(ByteBuffer buffer, int payloadSize) {
        buffer.position(0);
        fillHeader(buffer);
        backingBuffer = buffer;

        int udpTotalLength = UDP_HEADER_SIZE + payloadSize;
        backingBuffer.putShort(IP4_HEADER_SIZE + 4, (short) udpTotalLength);
        udpHeader.length = udpTotalLength;

        // Disable UDP checksum validation
        backingBuffer.putShort(IP4_HEADER_SIZE + 6, (short) 0);
        udpHeader.checksum = 0;

        int ip4TotalLength = IP4_HEADER_SIZE + udpTotalLength;
        backingBuffer.putShort(2, (short) ip4TotalLength);
        ip4Header.totalLength = ip4TotalLength;

        updateIP4Checksum();
        this.playLoadSize = payloadSize;
    }

    private void updateIP4Checksum() {
        ByteBuffer buffer = backingBuffer.duplicate();
        buffer.position(0);

        // Clear previous checksum
        buffer.putShort(10, (short) 0);

        int ipLength = ip4Header.headerLength;
        int sum = 0;
        while (ipLength > 0) {
            sum += BitUtils.getUnsignedShort(buffer.getShort());
            ipLength -= 2;
        }
        while (sum >> 16 > 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        sum = ~sum;
        ip4Header.headerChecksum = sum;
        backingBuffer.putShort(10, (short) sum);
    }

    //to control the data accuracyF
    private int updateTCPChecksum(int payloadSize) {
        int sum = 0;
        int tcpLength = TCP_HEADER_SIZE + payloadSize;

        // Calculate pseudo-header checksum
        ByteBuffer buffer = ByteBuffer.wrap(ip4Header.sourceAddress.getAddress());
        sum = BitUtils.getUnsignedShort(buffer.getShort()) + BitUtils.getUnsignedShort(buffer.getShort());

        buffer = ByteBuffer.wrap(ip4Header.destinationAddress.getAddress());
        sum += BitUtils.getUnsignedShort(buffer.getShort()) + BitUtils.getUnsignedShort(buffer.getShort());

        sum += IP4Header.TransportProtocol.TCP.getNumber() + tcpLength;

        buffer = backingBuffer.duplicate();
        // Clear previous checksum
        buffer.putShort(IP4_HEADER_SIZE + 16, (short) 0);

        // Calculate TCP segment checksum
        buffer.position(IP4_HEADER_SIZE);
        while (tcpLength > 1) {
            sum += BitUtils.getUnsignedShort(buffer.getShort());
            tcpLength -= 2;
        }
        if (tcpLength > 0) {
            sum += BitUtils.getUnsignedByte(buffer.get()) << 8;
        }
        while (sum >> 16 > 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        sum = ~sum;
        tcpHeader.checksum = sum;
        backingBuffer.putShort(IP4_HEADER_SIZE + 16, (short) sum);
        return sum;
    }

    private void fillHeader(ByteBuffer buffer) {
        ip4Header.fillHeader(buffer);
        if (isUDP) {
            udpHeader.fillHeader(buffer);
        } else if (isTCP) {
            tcpHeader.fillHeader(buffer);
        }

    }

    public String getIpAndPort() {
        String ipAndrPort;
        if (ip4Header == null) {
            return null;
        }
        int destinationPort;
        int sourcePort;
        InetAddress destinationAddress = ip4Header.destinationAddress;
        if (isUDP) {
            destinationPort = udpHeader.destinationPort;
            sourcePort = udpHeader.sourcePort;
            ipAndrPort = "UDP:" + destinationAddress.getHostAddress() + ":" + destinationPort + " " + sourcePort;
        } else {
            destinationPort = tcpHeader.destinationPort;
            sourcePort = tcpHeader.sourcePort;
            ipAndrPort = "TCP:" + destinationAddress.getHostAddress() + ":" + destinationPort + " " + sourcePort;
        }
        return ipAndrPort;
    }

    public String getSegmentHttpName() {
        if (!isTCP) {
            return null;
        }
        int lastPosition = backingBuffer.position();
        byte[] array = backingBuffer.array();
        isSSL = false;
        String headerString = new String(array, FIRST_TCP_DATA, playLoadSize);
        String[] headerLines = headerString.split("\\r\\n");
        hostName = getHttpHost(headerLines);
        backingBuffer.position(lastPosition);
        return hostName;
    }

    /**
     * 通过packet包发送的第一个数据判断类型
     * 如果是http协议 则首先发送的是http的请求头，请求头首先声明请求的类型 包括GET Head POST PUT OPTION TRACE CONNECT
     * 如果是https协议，则完成socket连接之后开始ssl握手，ssl握手时客户端发给服务器的的第一个包的包内容中包括了访问的域名
     */
    public void parseHttpRequestHeader() {
        if (!isTCP) {
            return;
        }
        int lastPosition = backingBuffer.position();
        byte[] array = backingBuffer.array();
        byte firsByte = array[FIRST_TCP_DATA];
        try {
            switch (firsByte) {
                //GET
                case 'G':
                    //HEAD
                case 'H':
                    //POST, PUT
                case 'P':
                    //DELETE
                case 'D':
                    //OPTIONS
                case 'O':
                    //TRACE
                case 'T':
                    //CONNECT
                case 'C':
                    isHttp = true;
                    getHttpHostAndRequestUrl(array);
                    break;
                //SSL
                case 0x16:
                    getSNI(array);
                    break;
                default:
                    cannotParse = true;
                    isSSL = false;
                    Log.d(TAG, "can not parse " + (firsByte & 0xFF) + "   " + ((char) firsByte));
                    break;


            }


        } catch (Exception e) {

        } finally {
            backingBuffer.position(lastPosition);
        }

    }

    private void getSNI(byte[] buffer) {
        isSSL = true;
        int offset = FIRST_TCP_DATA;
        int count = playLoadSize;
        int limit = offset + count;
        if (count > 43 && buffer[offset] == 0x16) { //TLS Client Hello
            offset += 43; //Skip 43 byte header

            //read sessionID
            if (offset + 1 > limit) {
                return;
            }
            int sessionIDLength = buffer[offset++] & 0xFF;
            offset += sessionIDLength;

            //read cipher suites
            if (offset + 2 > limit) {
                return;
            }

            int cipherSuitesLength = CommonMethods.readShort(buffer, offset) & 0xFFFF;
            offset += 2;
            offset += cipherSuitesLength;

            //read Compression method.
            if (offset + 1 > limit) {
                return;
            }
            int compressionMethodLength = buffer[offset++] & 0xFF;
            offset += compressionMethodLength;
            if (offset == limit) {
                VPNLog.w(TAG, "TLS Client Hello packet doesn't contains SNI info.(offset == limit)");
                return;
            }

            //read Extensions
            if (offset + 2 > limit) {
                return;
            }
            int extensionsLength = CommonMethods.readShort(buffer, offset) & 0xFFFF;
            offset += 2;

            if (offset + extensionsLength > limit) {
                VPNLog.w(TAG, "TLS Client Hello packet is incomplete.");
                return;
            }

            while (offset + 4 <= limit) {
                int type0 = buffer[offset++] & 0xFF;
                int type1 = buffer[offset++] & 0xFF;
                int length = CommonMethods.readShort(buffer, offset) & 0xFFFF;
                offset += 2;

                if (type0 == 0x00 && type1 == 0x00 && length > 5) {
                    offset += 5;
                    length -= 5;
                    if (offset + length > limit) {
                        return;
                    }
                    String serverName = new String(buffer, offset, length);
                    VPNLog.i("SNI: %s\n", serverName);
                    isSSL = true;
                    hostName = serverName;
                    return;
                } else {
                    offset += length;
                }

            }
            VPNLog.e(TAG, "TLS Client Hello packet doesn't contains Host field info.");
            return;
        } else {
            VPNLog.e(TAG, "Bad TLS Client Hello packet.");
            return;
        }
    }

    private void getHttpHostAndRequestUrl(byte[] array) {
        isSSL = false;
        String headerString = new String(array, FIRST_TCP_DATA, playLoadSize);
        String[] headerLines = headerString.split("\\r\\n");
        String host = getHttpHost(headerLines);
        if (!TextUtils.isEmpty(host)) {
            this.hostName = host;
        }
        String[] parts = headerLines[0].trim().split(" ");
        if (parts.length == 3 || parts.length == 2) {
            method = parts[0];
            urlPath = parts[1];
            VPNLog.d(TAG, "urlPath is " + urlPath);
            if (urlPath.startsWith("/")) {
                if (hostName != null) {
                    requestUrl = "http://" + hostName + urlPath;
                }
            } else if (urlPath.startsWith("http")) {
                requestUrl = urlPath;
            } else {
                requestUrl = "http://" + urlPath;
            }
        }
    }


    private String getHttpHost(String[] headerLines) {
        for (int i = 1; i < headerLines.length; i++) {
            String[] nameValueStrings = headerLines[i].split(":");
            if (nameValueStrings.length == 2 || nameValueStrings.length == 3) {
                String name = nameValueStrings[0].toLowerCase(Locale.ENGLISH).trim();
                String value = nameValueStrings[1].trim();
                if ("host".equals(name)) {
                    Log.d(TAG, "value is " + value);
                    return value;
                }
            }
        }
        return null;
    }


    public boolean isHttp() {
        return isHttp;
    }

    public String getUrlPath() {
        return urlPath;
    }

    /**
     * IP头部总共20个字节
     * char m_cVersionAndHeaderLen;     　　//版本信息(前4位)，头长度(后4位)  1个
     * char m_cTypeOfService;      　　　　　 // 服务类型8位    1个
     * short m_sTotalLenOfPacket;    　　　　//数据包长度      2个
     * short m_sPacketID;      　　　　　　　 //数据包标识      2个
     * short m_sSliceinfo;      　　　　　　　  //分片使用     2个
     * char m_cTTL;        　　　　　　　　　　//存活时间       1个
     * char m_cTypeOfProtocol;    　　　　　 //协议类型       1个
     * short m_sCheckSum;      　　　　　　 //校验和          2个
     * unsigned int m_uiSourIp;     　　　　　//源ip         4个
     * unsigned int m_uiDestIp;     　　　　　//目的ip        4个
     */
    public static class IP4Header implements Serializable {

        public byte version;
        byte IHL;
        int headerLength;
        short typeOfService;
        int totalLength;

        int identificationAndFlagsAndFragmentOffset;

        short TTL;
        private short protocolNum;
        TransportProtocol protocol;
        int headerChecksum;

        public InetAddress sourceAddress;
        public InetAddress destinationAddress;

        int optionsAndPadding;

        IP4Header duplicate() {
            IP4Header ip4Header = new IP4Header();
            ip4Header.version = version;
            ip4Header.IHL = IHL;
            ip4Header.headerLength = headerLength;
            ip4Header.typeOfService = typeOfService;
            ip4Header.totalLength = totalLength;

            ip4Header.identificationAndFlagsAndFragmentOffset = identificationAndFlagsAndFragmentOffset;

            ip4Header.TTL = TTL;
            ip4Header.protocolNum = protocolNum;
            ip4Header.protocol = protocol;
            ip4Header.headerChecksum = headerChecksum;

            ip4Header.sourceAddress = sourceAddress;
            ip4Header.destinationAddress = destinationAddress;

            ip4Header.optionsAndPadding = optionsAndPadding;
            return ip4Header;
        }

        private IP4Header() {

        }

        private enum TransportProtocol {
            TCP(6),
            UDP(17),
            Other(0xFF);

            private int protocolNumber;

            TransportProtocol(int protocolNumber) {
                this.protocolNumber = protocolNumber;
            }

            private static TransportProtocol numberToEnum(int protocolNumber) {
                if (protocolNumber == 6) {
                    return TCP;
                } else if (protocolNumber == 17) {
                    return UDP;
                } else {
                    return Other;
                }

            }

            public int getNumber() {
                return this.protocolNumber;
            }
        }

        private IP4Header(ByteBuffer buffer) throws UnknownHostException {
            byte versionAndIHL = buffer.get();
            this.version = (byte) (versionAndIHL >> 4);
            this.IHL = (byte) (versionAndIHL & 0x0F);
            this.headerLength = this.IHL << 2;

            this.typeOfService = BitUtils.getUnsignedByte(buffer.get());
            this.totalLength = BitUtils.getUnsignedShort(buffer.getShort());

            this.identificationAndFlagsAndFragmentOffset = buffer.getInt();

            this.TTL = BitUtils.getUnsignedByte(buffer.get());
            this.protocolNum = BitUtils.getUnsignedByte(buffer.get());
            this.protocol = TransportProtocol.numberToEnum(protocolNum);
            this.headerChecksum = BitUtils.getUnsignedShort(buffer.getShort());

            byte[] addressBytes = new byte[4];
            buffer.get(addressBytes, 0, 4);
            this.sourceAddress = InetAddress.getByAddress(addressBytes);

            buffer.get(addressBytes, 0, 4);
            this.destinationAddress = InetAddress.getByAddress(addressBytes);

            //this.optionsAndPadding = buffer.getInt();
        }

        void fillHeader(ByteBuffer buffer) {
            buffer.put((byte) (this.version << 4 | this.IHL));
            buffer.put((byte) this.typeOfService);
            buffer.putShort((short) this.totalLength);

            buffer.putInt(this.identificationAndFlagsAndFragmentOffset);

            buffer.put((byte) this.TTL);
            buffer.put((byte) this.protocol.getNumber());
            buffer.putShort((short) this.headerChecksum);

            buffer.put(this.sourceAddress.getAddress());
            buffer.put(this.destinationAddress.getAddress());
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("IP4Header{");
            sb.append("version=").append(version);
            sb.append(", IHL=").append(IHL);
            sb.append(", typeOfService=").append(typeOfService);
            sb.append(", totalLength=").append(totalLength);
            sb.append(", identificationAndFlagsAndFragmentOffset=").append(identificationAndFlagsAndFragmentOffset);
            sb.append(", TTL=").append(TTL);
            sb.append(", protocol=").append(protocolNum).append(":").append(protocol);
            sb.append(", headerChecksum=").append(headerChecksum);
            sb.append(", sourceAddress=").append(sourceAddress.getHostAddress());
            sb.append(", destinationAddress=").append(destinationAddress.getHostAddress());
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * short m_sSourPort;        　　　　　　// 源端口号16bit
     * short m_sDestPort;       　　　　　　 // 目的端口号16bit
     * unsigned int m_uiSequNum;       　　// 序列号32bit
     * unsigned int m_uiAcknowledgeNum;  // 确认号32bit
     * short m_sHeaderLenAndFlag;      　　// 前4位：TCP头长度；中6位：保留；后6位：标志位
     * short m_sWindowSize;       　　　　　// 窗口大小16bit
     * short m_sCheckSum;        　　　　　 // 检验和16bit
     * short m_surgentPointer;      　　　　 // 紧急数据偏移量16bit
     */
    public static class TCPHeader implements Serializable {
        /**
         * 会话结束
         */

        public static final int FIN = 0x01;
        /**
         * 开始会话请求
         */

        public static final int SYN = 0x02;
        /**
         * 复位 中断一个链接
         */

        public static final int RST = 0x04;
        /**
         * 数据包立即推送
         */
        public static final int PSH = 0x08;
        /**
         * 应答
         */
        public static final int ACK = 0x10;
        /**
         * 紧急
         */
        public static final int URG = 0x20;

        public int sourcePort;
        public int destinationPort;

        public long sequenceNumber;
        public long acknowledgementNumber;

        public byte dataOffsetAndReserved;
        public int headerLength;
        public byte flags;
        public int window;

        int checksum;
        int urgentPointer;

        byte[] optionsAndPadding;

        private TCPHeader(ByteBuffer buffer) {
            this.sourcePort = BitUtils.getUnsignedShort(buffer.getShort());
            this.destinationPort = BitUtils.getUnsignedShort(buffer.getShort());

            this.sequenceNumber = BitUtils.getUnsignedInt(buffer.getInt());
            this.acknowledgementNumber = BitUtils.getUnsignedInt(buffer.getInt());

            this.dataOffsetAndReserved = buffer.get();
            this.headerLength = (this.dataOffsetAndReserved & 0xF0) >> 2;
            this.flags = buffer.get();
            this.window = BitUtils.getUnsignedShort(buffer.getShort());

            this.checksum = BitUtils.getUnsignedShort(buffer.getShort());
            this.urgentPointer = BitUtils.getUnsignedShort(buffer.getShort());

            int optionsLength = this.headerLength - TCP_HEADER_SIZE;
            if (optionsLength > 0) {
                optionsAndPadding = new byte[optionsLength];
                buffer.get(optionsAndPadding, 0, optionsLength);
            }
        }

        TCPHeader() {

        }

        boolean isFIN() {
            return (flags & FIN) == FIN;
        }

        boolean isSYN() {
            return (flags & SYN) == SYN;
        }

        boolean isRST() {
            return (flags & RST) == RST;
        }

        boolean isPSH() {
            return (flags & PSH) == PSH;
        }

        boolean isACK() {
            return (flags & ACK) == ACK;
        }

        boolean isURG() {
            return (flags & URG) == URG;
        }

        int getWindow() {
            return window;
        }

        void fillHeader(ByteBuffer buffer) {
            buffer.putShort((short) sourcePort);
            buffer.putShort((short) destinationPort);

            buffer.putInt((int) sequenceNumber);
            buffer.putInt((int) acknowledgementNumber);

            buffer.put(dataOffsetAndReserved);
            buffer.put(flags);
            buffer.putShort((short) window);

            buffer.putShort((short) checksum);
            buffer.putShort((short) urgentPointer);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("TCPHeader{");
            sb.append("sourcePort=").append(sourcePort);
            sb.append(", destinationPort=").append(destinationPort);
            sb.append(", sequenceNumber=").append(sequenceNumber);
            sb.append(", acknowledgementNumber=").append(acknowledgementNumber);
            sb.append(", headerLength=").append(headerLength);
            sb.append(", clientWindow=").append(window);
            sb.append(", checksum=").append(checksum);
            sb.append(", flags=");
            if (isFIN()) {
                sb.append(" FIN");
            }
            if (isSYN()) {
                sb.append(" SYN");
            }
            if (isRST()) {
                sb.append(" RST");
            }

            if (isPSH()) {
                sb.append(" PSH");
            }
            if (isACK()) {
                sb.append(" ACK");
            }
            if (isURG()) {
                sb.append(" URG");
            }
            sb.append('}');
            return sb.toString();
        }

        TCPHeader duplicate() {
            TCPHeader tcpHeader = new TCPHeader();
            tcpHeader.sourcePort = sourcePort;
            tcpHeader.destinationPort = destinationPort;
            tcpHeader.sequenceNumber = sequenceNumber;
            tcpHeader.acknowledgementNumber = acknowledgementNumber;
            tcpHeader.dataOffsetAndReserved = dataOffsetAndReserved;
            tcpHeader.headerLength = headerLength;
            tcpHeader.flags = flags;
            tcpHeader.window = window;
            tcpHeader.checksum = checksum;
            tcpHeader.urgentPointer = urgentPointer;
            tcpHeader.optionsAndPadding = optionsAndPadding;
            return tcpHeader;
        }
    }

    public static class UDPHeader implements Serializable {
        public int sourcePort;
        public int destinationPort;

        public int length;
        public int checksum;

        UDPHeader(ByteBuffer buffer) {
            this.sourcePort = BitUtils.getUnsignedShort(buffer.getShort());
            this.destinationPort = BitUtils.getUnsignedShort(buffer.getShort());

            this.length = BitUtils.getUnsignedShort(buffer.getShort());
            this.checksum = BitUtils.getUnsignedShort(buffer.getShort());
        }

        UDPHeader() {

        }

        void fillHeader(ByteBuffer buffer) {
            buffer.putShort((short) this.sourcePort);
            buffer.putShort((short) this.destinationPort);

            buffer.putShort((short) this.length);
            buffer.putShort((short) this.checksum);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("UDPHeader{");
            sb.append("sourcePort=").append(sourcePort);
            sb.append(", destinationPort=").append(destinationPort);
            sb.append(", playoffSize=").append(length);
            sb.append(", checksum=").append(checksum);
            sb.append('}');
            return sb.toString();
        }

        UDPHeader duplicate() {
            UDPHeader udpHeader = new UDPHeader();
            udpHeader.sourcePort = sourcePort;
            udpHeader.destinationPort = destinationPort;
            udpHeader.length = length;
            udpHeader.checksum = checksum;
            return udpHeader;
        }
    }

    private static class BitUtils {
        private static short getUnsignedByte(byte value) {
            return (short) (value & 0xFF);
        }

        private static int getUnsignedShort(short value) {
            return value & 0xFFFF;
        }

        private static long getUnsignedInt(int value) {
            return value & 0xFFFFFFFFL;
        }
    }
}
