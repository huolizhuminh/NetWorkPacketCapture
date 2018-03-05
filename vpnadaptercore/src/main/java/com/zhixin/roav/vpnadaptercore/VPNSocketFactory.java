package com.zhixin.roav.vpnadaptercore;

import android.net.VpnService;
import android.provider.Settings;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;

import javax.net.SocketFactory;

/**
 * Created by minhui.zhu on 2017/7/20.
 * Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class VPNSocketFactory extends SocketFactory {
    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {

        return new VPNProtectedSocket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {

        return new VPNProtectedSocket(host, port, localHost, localPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return new VPNProtectedSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {

        return new VPNProtectedSocket(address, port, localAddress, localPort);
    }

    @Override
    public Socket createSocket() throws IOException {
        return new VPNProtectedSocket();
    }

    public static class VPNProtectedSocket extends Socket {

        private static final String TAG = VPNProtectedSocket.class.getSimpleName();


        public VPNProtectedSocket(String host, int port) throws IOException {
            super(host, port);
        }

        VPNProtectedSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            super(host, port, localHost, localPort);
        }

        VPNProtectedSocket(InetAddress host, int port) throws IOException {
            super(host, port);
        }

        VPNProtectedSocket() {
            super();
        }

        VPNProtectedSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            super(address, port, localAddress, localPort);
        }


        @Override
        public void connect(SocketAddress endpoint, int timeout) throws IOException {
            VpnService vpnService = LocalVPNService.getInstance();
            InetSocketAddress epoint = (InetSocketAddress) endpoint;
            InetAddress addr = epoint.getAddress();
            if (!VPNConnectManager.getInstance().isDeviceAddress(addr.getHostAddress())) {
                super.connect(endpoint, timeout);
                return;
            }

            if (!VpnUtils.needCheckWriteSetting()
                    || Settings.System.canWrite(LocalVpnInit.getContext())) {
                if (vpnService != null) {
                    vpnService.protect(this);
                }
                int bindResult = SocketUtils.strongBindWifiSocket(this);
                VPNLog.d(TAG, "bindResult is " + bindResult);
            }
            super.connect(endpoint, timeout);
        }
    }
    // If you want to connect device with socket directly.use the socket this API provide.
    public static Socket getDirectSocket(){
        Socket socket = null;
        try {
            socket = SocketChannel.open().socket();
            VpnService vpnService = LocalVPNService.getInstance();
            if (!VpnUtils.needCheckWriteSetting()
                    || Settings.System.canWrite(LocalVpnInit.getContext())) {
                if (vpnService != null) {
                    vpnService.protect(socket);
                }
                SocketUtils.strongBindWifiSocket(socket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return socket;
    }
}

