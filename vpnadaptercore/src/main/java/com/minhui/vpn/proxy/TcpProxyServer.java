package com.minhui.vpn.proxy;


import com.minhui.vpn.KeyHandler;
import com.minhui.vpn.VPNLog;
import com.minhui.vpn.nat.NatSession;
import com.minhui.vpn.nat.NatSessionManager;
import com.minhui.vpn.tunnel.TcpTunnel;
import com.minhui.vpn.tunnel.TunnelFactory;
import com.minhui.vpn.utils.AppDebug;
import com.minhui.vpn.utils.DebugLog;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by zengzheying on 15/12/30.
 */
public class TcpProxyServer implements Runnable {
    private static final String TAG = "TcpProxyServer";
    public boolean Stopped;
    public short port;

    Selector mSelector;
    ServerSocketChannel mServerSocketChannel;
    Thread mServerThread;

    public TcpProxyServer(int port) throws IOException {
        mSelector = Selector.open();

        mServerSocketChannel = ServerSocketChannel.open();
        mServerSocketChannel.configureBlocking(false);
        mServerSocketChannel.socket().bind(new InetSocketAddress(port));
        mServerSocketChannel.register(mSelector, SelectionKey.OP_ACCEPT);
        this.port = (short) mServerSocketChannel.socket().getLocalPort();

        DebugLog.i("AsyncTcpServer listen on %s:%d success.\n", mServerSocketChannel.socket().getInetAddress()
                .toString(), this.port & 0xFFFF);
    }

    /**
     * 启动TcpProxyServer线程
     */
    public void start() {
        mServerThread = new Thread(this, "TcpProxyServerThread");
        mServerThread.start();
    }

    public void stop() {
        this.Stopped = true;
        if (mSelector != null) {
            try {
                mSelector.close();
                mSelector = null;
            } catch (Exception ex) {

                DebugLog.e("TcpProxyServer mSelector.close() catch an exception: %s", ex);
            }
        }

        if (mServerSocketChannel != null) {
            try {
                mServerSocketChannel.close();
                mServerSocketChannel = null;
            } catch (Exception ex) {
                if (AppDebug.IS_DEBUG) {
                    ex.printStackTrace(System.err);
                }

                DebugLog.e("TcpProxyServer mServerSocketChannel.close() catch an exception: %s", ex);
            }
        }
    }


    @Override
    public void run() {
        try {
            while (true) {
                int select = mSelector.select();
                if (select == 0) {
                    Thread.sleep(5);
                    continue;
                }
                Set<SelectionKey> selectionKeys = mSelector.selectedKeys();
                if (selectionKeys == null) {
                    continue;
                }

                Iterator<SelectionKey> keyIterator = mSelector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    if (key.isValid()) {
                        try {
                            if (key.isAcceptable()) {
                                VPNLog.d(TAG, "isAcceptable");
                                onAccepted(key);
                            } else {
                                Object attachment = key.attachment();
                                if (attachment instanceof KeyHandler) {
                                    ((KeyHandler) attachment).onKeyReady(key);
                                }
                            }

                        } catch (Exception ex) {
                            if (AppDebug.IS_DEBUG) {
                                ex.printStackTrace(System.err);
                            }

                            DebugLog.e("udp iterate SelectionKey catch an exception: %s", ex);
                        }
                    }
                    keyIterator.remove();
                }


            }
        } catch (Exception e) {
            if (AppDebug.IS_DEBUG) {
                e.printStackTrace(System.err);
            }

            DebugLog.e("updServer catch an exception: %s", e);
        } finally {
            this.stop();
            DebugLog.i("udpServer thread exited.");
        }
    }

    InetSocketAddress getDestAddress(SocketChannel localChannel) {
        short portKey = (short) localChannel.socket().getPort();
        NatSession session = NatSessionManager.getSession(portKey);
        if (session != null) {
            return new InetSocketAddress(localChannel.socket().getInetAddress(), session.remotePort & 0xFFFF);
        }
        return null;
    }

    void onAccepted(SelectionKey key) {
        TcpTunnel localTunnel = null;
        try {
            SocketChannel localChannel = mServerSocketChannel.accept();
            localTunnel = TunnelFactory.wrap(localChannel, mSelector);
            short portKey = (short) localChannel.socket().getPort();
            InetSocketAddress destAddress = getDestAddress(localChannel);
            if (destAddress != null) {
                TcpTunnel remoteTunnel = TunnelFactory.createTunnelByConfig(destAddress, mSelector, portKey);
                //关联兄弟
                remoteTunnel.setIsHttpsRequest(localTunnel.isHttpsRequest());
                remoteTunnel.setBrotherTunnel(localTunnel);
                localTunnel.setBrotherTunnel(remoteTunnel);
                //开始连接
                remoteTunnel.connect(destAddress);
            }
        } catch (Exception ex) {
            if (AppDebug.IS_DEBUG) {
                ex.printStackTrace(System.err);
            }

            DebugLog.e("TcpProxyServer onAccepted catch an exception: %s", ex);

            if (localTunnel != null) {
                localTunnel.dispose();
            }
        }
    }

}
