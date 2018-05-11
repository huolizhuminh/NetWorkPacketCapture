package com.minhui.vpn.service;

import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.support.annotation.RequiresApi;
import android.util.Log;


import com.minhui.vpn.LocalVPNService;
import com.minhui.vpn.Packet;
import com.minhui.vpn.ProxyConfig;
import com.minhui.vpn.UDPServer;
import com.minhui.vpn.VPNConnectManager;
import com.minhui.vpn.VPNLog;
import com.minhui.vpn.builder.HtmlBlockingInfoBuilder;
import com.minhui.vpn.dns.DnsPacket;
import com.minhui.vpn.filter.BlackListFilter;
import com.minhui.vpn.http.HttpRequestHeaderParser;
import com.minhui.vpn.nat.NatSession;
import com.minhui.vpn.nat.NatSessionManager;
import com.minhui.vpn.proxy.DnsProxy;
import com.minhui.vpn.proxy.TcpProxyServer;
import com.minhui.vpn.tcpip.IPHeader;
import com.minhui.vpn.tcpip.TCPHeader;
import com.minhui.vpn.tcpip.UDPHeader;
import com.minhui.vpn.utils.AppDebug;
import com.minhui.vpn.utils.CommonMethods;
import com.minhui.vpn.utils.DebugLog;
import com.minhui.vpn.utils.VpnServiceHelper;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * Created by zengzheying on 15/12/28.
 */
public class FirewallVpnService extends VpnService implements Runnable {
    public static final String ACTION_START_VPN = "com.minhui.START_VPN";
    public static final String ACTION_CLOSE_VPN = "com.minhui.roav.CLOSE_VPN";
    private static final String FACEBOOK_APP = "com.facebook.katana";
    private static final String YOUTUBE_APP = "com.google.android.youtube";
    private static final String GOOGLE_MAP_APP = "com.google.android.apps.maps";

    private static final String VPN_ADDRESS = "10.0.0.2"; // Only IPv4 support for now
    private static final String VPN_ROUTE = "0.0.0.0"; // Intercept everything
    private static final String GOOGLE_DNS_FIRST = "8.8.8.8";
    private static final String GOOGLE_DNS_SECOND = "8.8.4.4";
    private static final String AMERICA = "208.67.222.222";
    private static final String HK_DNS_SECOND = "205.252.144.228";
    private static final String CHINA_DNS_FIRST = "114.114.114.114";
    public static final String BROADCAST_VPN_STATE = "com.minhui.localvpn.VPN_STATE";
    public static final String SELECT_PACKAGE_ID = "select_protect_package_id";
    private static final String TAG = "FirewallVpnService";
    private static int ID;
    private static int LOCAL_IP;
    private boolean IsRunning = false;
    private Thread mVPNThread;
    private ParcelFileDescriptor mVPNInterface;
    private TcpProxyServer mTcpProxyServer;
    // private DnsProxy mDnsProxy;
    private FileOutputStream mVPNOutputStream;

    private byte[] mPacket;
    private IPHeader mIPHeader;
    private TCPHeader mTCPHeader;
    private UDPHeader mUDPHeader;
    private ByteBuffer mDNSBuffer;
    private Handler mHandler;
    private long mSentBytes;
    private long mReceivedBytes;
    private ConcurrentLinkedQueue<Packet> udpQueue;
    private FileInputStream in;
    private UDPServer udpServer;
    private String selectPackage;
    public static final int MUTE_SIZE = 2560;

    public FirewallVpnService() {
        ID++;
        mHandler = new Handler();
        mPacket = new byte[MUTE_SIZE];
        mIPHeader = new IPHeader(mPacket, 0);
        //Offset = ip报文头部长度
        mTCPHeader = new TCPHeader(mPacket, 20);
        mUDPHeader = new UDPHeader(mPacket, 20);
        //Offset = ip报文头部长度 + udp报文头部长度 = 28
        mDNSBuffer = ((ByteBuffer) ByteBuffer.wrap(mPacket).position(28)).slice();

        VpnServiceHelper.onVpnServiceCreated(this);

        DebugLog.i("New VPNService(%d)\n", ID);
    }

    //启动Vpn工作线程
    @Override
    public void onCreate() {
        DebugLog.i("VPNService(%s) created.\n", ID);
        mVPNThread = new Thread(this, "VPNServiceThread");
        mVPNThread.start();
        setVpnRunningStatus(true);
        //   notifyStatus(new VPNEvent(VPNEvent.Status.STARTING));
        super.onCreate();
    }

    //只设置IsRunning = true;
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    //停止Vpn工作线程
    @Override
    public void onDestroy() {
        DebugLog.i("VPNService(%s) destroyed.\n", ID);
        if (mVPNThread != null) {
            mVPNThread.interrupt();
        }
        VpnServiceHelper.onVpnServiceDestroy();
        super.onDestroy();
    }

    //发送UDP数据报
    public void sendUDPPacket(IPHeader ipHeader, UDPHeader udpHeader) {
        try {
            CommonMethods.ComputeUDPChecksum(ipHeader, udpHeader);
            this.mVPNOutputStream.write(ipHeader.mData, ipHeader.mOffset, ipHeader.getTotalLength());
        } catch (IOException e) {
            if (AppDebug.IS_DEBUG) {
                e.printStackTrace();
            }

            DebugLog.e("VpnService send UDP packet catch an exception %s", e);
        }
    }

    //建立VPN，同时监听出口流量
    private void runVPN() throws Exception {

        this.mVPNInterface = establishVPN();
        startReadAndWrite();
    }

    private void startReadAndWrite() throws Exception {
        int size = 0;
        this.mVPNOutputStream = new FileOutputStream(mVPNInterface.getFileDescriptor());
        in = new FileInputStream(mVPNInterface.getFileDescriptor());
        while (size != -1 && IsRunning) {
            boolean hasWrite = false;
            size = in.read(mPacket);
            if (size > 0) {
                if (mTcpProxyServer.Stopped) {
                    in.close();
                    throw new Exception("LocalServer stopped.");
                }
                hasWrite = onIPPacketReceived(mIPHeader, size);

            }
            if (!hasWrite) {
                Packet packet = udpQueue.poll();
                if (packet != null) {
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
                    mVPNOutputStream.write(bufferFromNetwork.array());

                }
            }
            Thread.sleep(100);
        }
        in.close();
        disconnectVPN();
    }

    boolean onIPPacketReceived(IPHeader ipHeader, int size) throws IOException {
        boolean hasWrite = false;

        switch (ipHeader.getProtocol()) {
            case IPHeader.TCP:
                TCPHeader tcpHeader = mTCPHeader;
                tcpHeader.mOffset = ipHeader.getHeaderLength(); //矫正TCPHeader里的偏移量，使它指向真正的TCP数据地址
                if (tcpHeader.getSourcePort() == mTcpProxyServer.Port) {
                    VPNLog.d(TAG, "process  tcp packet from net ");
                    NatSession session = NatSessionManager.getSession(tcpHeader.getDestinationPort());
                    if (session != null) {
                        ipHeader.setSourceIP(ipHeader.getDestinationIP());
                        tcpHeader.setSourcePort(session.RemotePort);
                        ipHeader.setDestinationIP(LOCAL_IP);

                        CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);
                        mVPNOutputStream.write(ipHeader.mData, ipHeader.mOffset, size);
                        mReceivedBytes += size;
                    } else {
                        DebugLog.i("NoSession: %s %s\n", ipHeader.toString(), tcpHeader.toString());
                    }

                } else {
                    VPNLog.d(TAG, "process  tcp packet to net ");
                    //添加端口映射
                    int portKey = tcpHeader.getSourcePort();
                    NatSession session = NatSessionManager.getSession(portKey);
                    if (session == null || session.RemoteIP != ipHeader.getDestinationIP() || session.RemotePort
                            != tcpHeader.getDestinationPort()) {
                        session = NatSessionManager.createSession(portKey, ipHeader.getDestinationIP(), tcpHeader
                                .getDestinationPort());
                    }

                    session.LastNanoTime = System.nanoTime();
                    session.PacketSent++; //注意顺序

                    int tcpDataSize = ipHeader.getDataLength() - tcpHeader.getHeaderLength();
                    if (session.PacketSent == 2 && tcpDataSize == 0) {
                        return false; //丢弃tcp握手的第二个ACK报文。因为客户端发数据的时候也会带上ACK，这样可以在服务器Accept之前分析出HOST信息。
                    }

                    //分析数据，找到host
                    if (session.BytesSent == 0 && tcpDataSize > 10) {
                        int dataOffset = tcpHeader.mOffset + tcpHeader.getHeaderLength();
                        HttpRequestHeaderParser.parseHttpRequestHeader(session, tcpHeader.mData, dataOffset,
                                tcpDataSize);
                        DebugLog.i("Host: %s\n", session.RemoteHost);
                        DebugLog.i("Request: %s %s\n", session.Method, session.RequestUrl);
                    }

                    //转发给本地TCP服务器
                    ipHeader.setSourceIP(ipHeader.getDestinationIP());
                    ipHeader.setDestinationIP(LOCAL_IP);
                    tcpHeader.setDestinationPort(mTcpProxyServer.Port);

                    CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);
                    mVPNOutputStream.write(ipHeader.mData, ipHeader.mOffset, size);
                    session.BytesSent += tcpDataSize; //注意顺序
                    mSentBytes += size;
                }
                hasWrite = true;
                break;
            case IPHeader.UDP:
                Log.d(TAG, "process udp packet");
                byte[] bytes = Arrays.copyOf(mPacket, mPacket.length);
                ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, 0, size);
                byteBuffer.limit(size);
                //    byteBuffer.flip();
                VPNLog.d(TAG, "start new Packet");
                Packet packet = new Packet(byteBuffer);
                VPNLog.d(TAG, "end new packet");
                udpServer.processUDPPacket(packet);
                VPNLog.d(TAG, "end processUDPPacket");

                break;
            default:
                break;
        }
        return hasWrite;

    }

    private void waitUntilPrepared() {
        while (prepare(this) != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                if (AppDebug.IS_DEBUG) {
                    e.printStackTrace();
                }
                DebugLog.e("waitUntilPrepared catch an exception %s\n", e);
            }
        }
    }

    private ParcelFileDescriptor establishVPN() throws Exception {
    /*    //  notifyStatus(new VPNEvent(VPNEvent.Status.ESTABLISHED));
        Builder builder = new Builder();
        builder.addAddress(VPN_ADDRESS, 32);
        builder.addRoute(VPN_ROUTE, 0);
        //某些国外的手机例如google pixel 默认的dns解析器地址不是8.8.8.8 ，不设置会出错
        builder.addDnsServer(GOOGLE_DNS_FIRST);
        builder.addDnsServer(CHINA_DNS_FIRST);
        builder.addDnsServer(GOOGLE_DNS_SECOND);
        builder.addDnsServer(AMERICA);
        builder.setMtu(2560);
        try {
            if (selectPackage != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    builder.addAllowedApplication(selectPackage);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
      *//*   addAllowedApp(builder, YOUTUBE_APP);*//*
        return builder.setSession(VPNConnectManager.getInstance().getAppName()).establish();*/
        Builder builder = new Builder();
        builder.setMtu(MUTE_SIZE);

        DebugLog.i("setMtu: %d\n", ProxyConfig.Instance.getMTU());

        ProxyConfig.IPAddress ipAddress = ProxyConfig.Instance.getDefaultLocalIP();
        LOCAL_IP = CommonMethods.ipStringToInt(ipAddress.Address);
        builder.addAddress(ipAddress.Address, ipAddress.PrefixLength);
        DebugLog.i("addAddress: %s/%d\n", ipAddress.Address, ipAddress.PrefixLength);

        for (ProxyConfig.IPAddress dns : ProxyConfig.Instance.getDnsList()) {
            builder.addDnsServer(dns.Address);
            DebugLog.i("addDnsServer: %s\n", dns.Address);
        }

        if (ProxyConfig.Instance.getRouteList().size() > 0) {
            for (ProxyConfig.IPAddress routeAddress : ProxyConfig.Instance.getRouteList()) {
                builder.addRoute(routeAddress.Address, routeAddress.PrefixLength);
                DebugLog.i("addRoute: %s/%d\n", routeAddress.Address, routeAddress.PrefixLength);
            }
            builder.addRoute(CommonMethods.ipIntToInet4Address(ProxyConfig.FAKE_NETWORK_IP), 16);
            DebugLog.i("addRoute for FAKE_NETWORK: %s/%d\n", CommonMethods.ipIntToString(ProxyConfig.FAKE_NETWORK_IP),
                    16);
        } else {
            builder.addRoute("0.0.0.0", 0);
            DebugLog.i("addDefaultRoute: 0.0.0.0/0\n");
        }

        Class<?> SystemProperties = Class.forName("android.os.SystemProperties");
        Method method = SystemProperties.getMethod("get", new Class[]{String.class});
        ArrayList<String> servers = new ArrayList<>();
        for (String name : new String[]{"net.dns1", "net.dns2", "net.dns3", "net.dns4",}) {
            try {
                String value = (String) method.invoke(null, name);
                if (value != null && !"".equals(value) && !servers.contains(value)) {
                    servers.add(value);
                    builder.addRoute(value, 32); //添加路由，使得DNS查询流量也走该VPN接口

                    DebugLog.i("%s=%s\n", name, value);
                }
            }catch (Exception e){

            }

        }
        builder.setSession(ProxyConfig.Instance.getSessionName());
        ParcelFileDescriptor pfdDescriptor = builder.establish();
        //  notifyStatus(new VPNEvent(VPNEvent.Status.ESTABLISHED));
        return pfdDescriptor;
    }

    @Override
    public void run() {
        try {
            DebugLog.i("VPNService(%s) work thread is Running...\n", ID);

            waitUntilPrepared();

            ProxyConfig.Instance.setDomainFilter(new BlackListFilter());
            ProxyConfig.Instance.setBlockingInfoBuilder(new HtmlBlockingInfoBuilder());
            ProxyConfig.Instance.prepare();
            udpQueue = new ConcurrentLinkedQueue<>();

            //启动TCP代理服务
            mTcpProxyServer = new TcpProxyServer(0);
            mTcpProxyServer.start();
            udpServer = new UDPServer(this, udpQueue);
            udpServer.start();
         /*   mDnsProxy = new DnsProxy();
            mDnsProxy.start();*/
            DebugLog.i("DnsProxy started.\n");

            ProxyConfig.Instance.onVpnStart(this);
            while (IsRunning) {
                runVPN();
            }
            ProxyConfig.Instance.onVpnEnd(this);

        } catch (InterruptedException e) {
            if (AppDebug.IS_DEBUG) {
                e.printStackTrace();
            }
            DebugLog.e("VpnService run catch an exception %s.\n", e);
        } catch (Exception e) {
            if (AppDebug.IS_DEBUG) {
                e.printStackTrace();
            }
            DebugLog.e("VpnService run catch an exception %s.\n", e);
        } finally {
            DebugLog.i("VpnService terminated");
            dispose();
        }
    }

    public void disconnectVPN() {
        try {
            if (mVPNInterface != null) {
                mVPNInterface.close();
                mVPNInterface = null;
            }
        } catch (Exception e) {
            //ignore
        }
        // notifyStatus(new VPNEvent(VPNEvent.Status.UNESTABLISHED));
        this.mVPNOutputStream = null;
    }

    private synchronized void dispose() {
        //断开VPN
        disconnectVPN();

        //停止TCP代理服务
        if (mTcpProxyServer != null) {
            mTcpProxyServer.stop();
            mTcpProxyServer = null;
            DebugLog.i("TcpProxyServer stopped.\n");
        }
        udpServer.closeAllUDPConn();

        stopSelf();
        setVpnRunningStatus(false);
    }


    public boolean vpnRunningStatus() {
        return IsRunning;
    }

    public void setVpnRunningStatus(boolean isRunning) {
        IsRunning = isRunning;
    }
}
