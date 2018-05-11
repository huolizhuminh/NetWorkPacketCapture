package com.minhui.vpn.proxy;

import android.util.SparseArray;


import com.minhui.vpn.ProxyConfig;
import com.minhui.vpn.dns.DnsPacket;
import com.minhui.vpn.dns.Question;
import com.minhui.vpn.dns.Resource;
import com.minhui.vpn.dns.ResourcePointer;
import com.minhui.vpn.tcpip.IPHeader;
import com.minhui.vpn.tcpip.UDPHeader;
import com.minhui.vpn.utils.AppDebug;
import com.minhui.vpn.utils.CommonMethods;
import com.minhui.vpn.utils.DebugLog;
import com.minhui.vpn.utils.VpnServiceHelper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by zengzheying on 15/12/29.
 * DNS代理
 */
public class DnsProxy implements Runnable {

	private static final ConcurrentHashMap<Integer, String> IPDomainMaps = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, Integer> DomainIPMaps = new ConcurrentHashMap<>();
	private final long QUERY_TIMEOUT_NS = 10 * 1000 * 1000 * 1000L;
	private final SparseArray<QueryState> mQueryArray;
	public boolean Stopped;
	private DatagramSocket mClient;
	private Thread mReceivedThread;
	private short mQueryID;

	public DnsProxy() throws IOException {
		mQueryArray = new SparseArray<>();
		mClient = new DatagramSocket(0);
	}

	/**
	 * 根据ip查询域名
	 *
	 * @param ip ip地址
	 * @return 域名
	 */
	public static String reverseLookup(int ip) {
		return IPDomainMaps.get(ip);
	}


	/**
	 * 启动线程
	 */
	public void start() {
		mReceivedThread = new Thread(this, "DnsProxyThread");
		mReceivedThread.start();
	}

	/**
	 * 停止线程
	 */
	public void stop() {
		Stopped = true;
		if (mClient != null) {
			mClient.close();
			mClient = null;
		}
	}


	@Override
	public void run() {
		try {
			byte[] RECEIVE_BUFFER = new byte[20000];
			IPHeader ipHeader = new IPHeader(RECEIVE_BUFFER, 0);
			ipHeader.Default();
			int ipHeaderLength = 20;
			UDPHeader udpHeader = new UDPHeader(RECEIVE_BUFFER, ipHeaderLength);

			int udpHeaderLenght = 8;
			ByteBuffer dnsBuffer = ByteBuffer.wrap(RECEIVE_BUFFER);
			dnsBuffer.position(ipHeaderLength + udpHeaderLenght);
			dnsBuffer = dnsBuffer.slice();

			DatagramPacket packet = new DatagramPacket(RECEIVE_BUFFER, 28, RECEIVE_BUFFER.length - (ipHeaderLength +
					udpHeaderLenght));

			while (mClient != null && !mClient.isClosed()) {
				packet.setLength(RECEIVE_BUFFER.length - (ipHeaderLength + udpHeaderLenght));
				mClient.receive(packet);

				dnsBuffer.clear();
				dnsBuffer.limit(packet.getLength());
				try {
					DnsPacket dnsPacket = DnsPacket.fromBytes(dnsBuffer);
					if (dnsPacket != null) {
						OnDnsResponseReceived(ipHeader, udpHeader, dnsPacket);
					}
				} catch (Exception ex) {
					if (AppDebug.IS_DEBUG) {
						ex.printStackTrace(System.err);
					}

					DebugLog.e("Parse dns error: %s\n", ex);
				}
			}
		} catch (Exception e) {
			if (AppDebug.IS_DEBUG) {
				e.printStackTrace(System.err);
			}
			DebugLog.e("DnsProxy Thread catch an exception %s\n", e);
		} finally {
			DebugLog.i("DnsProxy Thread Exited.\n");
			this.stop();
		}
	}

	/**
	 * 从DNS响应报文中获取第一个IP地址
	 *
	 * @param dnsPacket DNS报文
	 * @return 第一个IP地址， 没有则返回0
	 */
	private int getFirstIP(DnsPacket dnsPacket) {
		for (int i = 0; i < dnsPacket.Header.ResourceCount; i++) {
			Resource resource = dnsPacket.Resources[i];
			if (resource.Type == 1) {
				int ip = CommonMethods.readInt(resource.Data, 0);
				return ip;
			}
		}
		return 0;
	}

	private void tamperDnsResponse(byte[] rawPacket, DnsPacket dnsPacket, int newIP) {
		Question question = dnsPacket.Questions[0]; //DNS的一个问题

		dnsPacket.Header.setResourceCount((short) 1);
		dnsPacket.Header.setAResourceCount((short) 0);
		dnsPacket.Header.setEResourceCount((short) 0);

		// 这里会有个疑问，在DNS报文中，只有头部是固定的，其他部分不一定，这个方法在DNS查询、回复中都有用到，
		// 理论上应该出现数组控件不足的情况吧（查询的DNS包只有头部部分）
		// 那么怎么这里的处理不用按情况分别增加数组空间呢？

		// 其实在DNS查询的时候，这里的rawPacket时LocalVpnService的m_Packet数组的空间
		// 在DNS回复的时候，这里的rawPacket其实是本类run方法的RECEIVE_BUFFER数组的空间
		// 两者的空间都足够大，所以不用增加数组空间
		ResourcePointer resourcePointer = new ResourcePointer(rawPacket, question.Offset() + question.Length());
		resourcePointer.setDomain((short) 0xC00C); //指针，指向问题区的域名
		resourcePointer.setType(question.Type);
		resourcePointer.setClass(question.Class);
		resourcePointer.setTTL(ProxyConfig.Instance.getDnsTTL());
		resourcePointer.setDataLength((short) 4);
		resourcePointer.setIP(newIP);

		// DNS报头长度 + 问题长度 + 资源记录长度（域名指针[2字节] + 类型[2字节] +
		// 类[2字节] + TTL[4字节] + 资源数据长度[2字节] + ip[4字节] = 16字节）
		dnsPacket.Size = 12 + question.Length() + 16;
	}

	/**
	 * 获取或创建一个指定域名的虚假IP地址
	 *
	 * @param domainString 指定域名
	 * @return 虚假IP地址
	 */
	private int getOrCreateFakeIP(String domainString) {
		Integer fakeIP = DomainIPMaps.get(domainString);
		if (fakeIP == null) {
			int hashIP = domainString.hashCode();
			do {
				fakeIP = ProxyConfig.FAKE_NETWORK_IP | (hashIP & 0x0000FFFF);
				hashIP++;
			} while (IPDomainMaps.containsKey(fakeIP));

			DomainIPMaps.put(domainString, fakeIP);
			IPDomainMaps.put(fakeIP, domainString);
		}
		return fakeIP;
	}

	/**
	 * 对收到的DNS答复进行修改，以达到DNS污染的目的
	 *
	 * @param rawPacket ip包的数据部分
	 * @param dnsPacket DNS数据包
	 * @return true: 修改了数据 false: 未修改数据
	 */
	private boolean dnsPollution(byte[] rawPacket, DnsPacket dnsPacket) {
		if (dnsPacket.Header.ResourceCount > 0) {
			Question question = dnsPacket.Questions[0];
			if (question.Type == 1) {
				int realIP = getFirstIP(dnsPacket);
				if (ProxyConfig.Instance.needProxy(question.Domain, realIP)) {
					int fakeIP = getOrCreateFakeIP(question.Domain);
					tamperDnsResponse(rawPacket, dnsPacket, fakeIP);

					DebugLog.i("FakeDns: %s=>%s(%s)\n", question.Domain, CommonMethods.ipIntToString(realIP),
							CommonMethods.ipIntToString(fakeIP));
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 收到Dns查询回复，对指定域名进行污染后，转发给发起请求的客户端
	 */
	private void OnDnsResponseReceived(IPHeader ipHeader, UDPHeader udpHeader, DnsPacket dnsPacket) {
		QueryState state = null;
		synchronized (mQueryArray) {
			state = mQueryArray.get(dnsPacket.Header.ID);
			if (state != null) {
				mQueryArray.remove(dnsPacket.Header.ID);
			}
		}

		if (state != null) {
			DebugLog.i("Received DNS result form Remote DNS Server");
			if (dnsPacket.Header.QuestionCount > 0 && dnsPacket.Header.ResourceCount > 0) {
				DebugLog.i("Real IP: %s ==> %s", dnsPacket.Questions[0].Domain, CommonMethods.ipIntToString(getFirstIP
						(dnsPacket)));
			}
			//DNS污染
			dnsPollution(udpHeader.mData, dnsPacket);

			dnsPacket.Header.setID(state.mClientQueryID);
			ipHeader.setSourceIP(state.mRemoteIP);
			ipHeader.setDestinationIP(state.mClientIP);
			ipHeader.setProtocol(IPHeader.UDP);
			// IP头部长度 + UDP头部长度 + DNS报文长度
			udpHeader.setTotalLength(20 + 8 + dnsPacket.Size);
			udpHeader.setSourcePort(state.mRemotePort);
			udpHeader.setDestinationPort(state.mClientPort);
			udpHeader.setTotalLength(8 + dnsPacket.Size);

			VpnServiceHelper.sendUDPPacket(ipHeader, udpHeader);
		}
	}

	/**
	 * 从缓冲中获取指定的域名的IP
	 *
	 * @param domain 指定域名
	 * @return 域名的IP地址
	 */
	private int getIPFromCache(String domain) {
		Integer ip = DomainIPMaps.get(domain);
		if (ip == null) {
			return 0;
		} else {
			return ip;
		}
	}

	/**
	 * 对符合过滤条件的域名（如是海外域名或者是gfw上的拦截域名），则直接构建一个提供虚假IP的DNS回复包
	 *
	 * @param ipHeader  ip报文
	 * @param udpHeader udp报文
	 * @param dnsPacket dns报文
	 * @return 构建了一个虚假的DNS回复包给查询客户端则返回true，否则false
	 */
	private boolean interceptDns(IPHeader ipHeader, UDPHeader udpHeader, DnsPacket dnsPacket) {
		Question question = dnsPacket.Questions[0];

		DebugLog.i("DNS query %s", question.Domain);

		if (question.Type == 1) {
			if (ProxyConfig.Instance.needProxy(question.Domain, getIPFromCache(question.Domain))) {
				int fakeIP = getOrCreateFakeIP(question.Domain);
				tamperDnsResponse(ipHeader.mData, dnsPacket, fakeIP);

				DebugLog.i("interceptDns FakeDns: %s=>%s\n", question.Domain, CommonMethods.ipIntToString(fakeIP));

				int sourceIP = ipHeader.getSourceIP();
				short sourcePort = udpHeader.getSourcePort();
				ipHeader.setSourceIP(ipHeader.getDestinationIP());
				ipHeader.setDestinationIP(sourceIP);
				//IP数据包数据长度 = ip数据报报头长度 + udp报头长度 + DNS报文长度
				ipHeader.setTotalLength(20 + 8 + dnsPacket.Size);
				udpHeader.setSourcePort(udpHeader.getDestinationPort());
				udpHeader.setDestinationPort(sourcePort);
				udpHeader.setTotalLength(8 + dnsPacket.Size);
				VpnServiceHelper.sendUDPPacket(ipHeader, udpHeader);
				return true;
			}
		}
		return false;
	}

	/**
	 * 清楚超时的查询
	 */
	private void clearExpiredQueries() {
		long now = System.nanoTime();
		for (int i = mQueryArray.size() - 1; i >= 0; i--) {
			QueryState state = mQueryArray.valueAt(i);
			if ((now - System.nanoTime()) > QUERY_TIMEOUT_NS) {
				mQueryArray.removeAt(i);
			}
		}
	}

	/**
	 * 收到APPs的DNS查询包，根据情况转发或者提供一个虚假的DNS回复数据报
	 */
	public void onDnsRequestReceived(IPHeader ipHeader, UDPHeader udpHeader, DnsPacket dnsPacket) {
		if (!interceptDns(ipHeader, udpHeader, dnsPacket)) {
			//转发DNS
			QueryState state = new QueryState();
			state.mClientQueryID = dnsPacket.Header.ID;
			state.mQueryNanoTime = System.nanoTime();
			state.mClientIP = ipHeader.getSourceIP();
			state.mClientPort = udpHeader.getSourcePort();
			state.mRemoteIP = ipHeader.getDestinationIP();
			state.mRemotePort = udpHeader.getDestinationPort();

			//转换QueryID
			mQueryID++;
			dnsPacket.Header.setID(mQueryID);

			synchronized (mQueryArray) {
				clearExpiredQueries(); //清空过期的查询，减少内存消耗
				mQueryArray.put(mQueryID, state);  //关联数据
			}

			//应该是DNS服务器的地址和端口
			InetSocketAddress remoteAddress = new InetSocketAddress(CommonMethods.ipIntToInet4Address(state.mRemoteIP),
					state.mRemotePort);
			//只需要把DNS数据报发送过去，不含UDP头部
			DatagramPacket packet = new DatagramPacket(udpHeader.mData, udpHeader.mOffset + 8, dnsPacket.Size);
			packet.setSocketAddress(remoteAddress);

			try {
				if (VpnServiceHelper.protect(mClient)) {
					//使用DatagramSocket发送DatagramPacket，读取也是用该DatagramSocket
					mClient.send(packet);
					DebugLog.i("Send an DNS Request Package to Remote DNS Server(%s)\n", CommonMethods.ipIntToString
							(state.mRemoteIP));
				} else {
					DebugLog.e("VpnService protect udp socket failed.");
				}
			} catch (IOException e) {
				if (AppDebug.IS_DEBUG) {
					e.printStackTrace(System.err);
				}
				DebugLog.e("Send Dns Request Package catch an exception %s\n", e);
			}
		}
	}

	private static class QueryState {
		public short mClientQueryID;
		public long mQueryNanoTime;
		public int mClientIP;
		public short mClientPort;
		public int mRemoteIP;
		public short mRemotePort;
	}
}
