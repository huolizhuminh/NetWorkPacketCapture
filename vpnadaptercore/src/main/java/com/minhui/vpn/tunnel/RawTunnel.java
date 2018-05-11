package com.minhui.vpn.tunnel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * Created by zengzheying on 15/12/30.
 */
public class RawTunnel extends Tunnel {

	public RawTunnel(SocketChannel innerChannel, Selector selector) {
		super(innerChannel, selector);
	}

	public RawTunnel(InetSocketAddress serverAddress, Selector selector) throws IOException {
		super(serverAddress, selector);
	}

	@Override
	protected void onConnected(ByteBuffer buffer) throws Exception {
		onTunnelEstablished();
	}

	@Override
	protected boolean isTunnelEstablished() {
		return true;
	}

	@Override
	protected void beforeSend(ByteBuffer buffer) throws Exception {

	}

	@Override
	protected void afterReceived(ByteBuffer buffer) throws Exception {

	}

	@Override
	protected void onDispose() {

	}
}
