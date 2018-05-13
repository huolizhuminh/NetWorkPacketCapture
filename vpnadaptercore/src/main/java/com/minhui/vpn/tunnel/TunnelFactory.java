package com.minhui.vpn.tunnel;


import com.minhui.vpn.nat.NatSession;
import com.minhui.vpn.nat.NatSessionManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * Created by zengzheying on 15/12/30.
 */
public class TunnelFactory {

	public static TcpTunnel wrap(SocketChannel channel, Selector selector) {
		TcpTunnel tunnel = new RawTcpTunnel(channel, selector);
		NatSession session = NatSessionManager.getSession((short) channel.socket().getPort());
		if (session != null) {
			tunnel.setIsHttpsRequest(session.isHttpsSession);
		}
		return tunnel;
	}

	public static TcpTunnel createTunnelByConfig(InetSocketAddress destAddress, Selector selector, short portKey) throws IOException {
		return new RemoteTcpTunnel(destAddress, selector,portKey);
	}
}
