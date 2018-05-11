package com.minhui.vpn.utils;


import android.util.Log;

import com.minhui.vpn.tcpip.IPHeader;
import com.minhui.vpn.tcpip.TCPHeader;
import com.minhui.vpn.tcpip.UDPHeader;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author zengzheying
 */
public class CommonMethods {

	private static final String TAG = "CommonMethods";

	public static InetAddress ipIntToInet4Address(int ip) {
		byte[] ipAddress = new byte[4];
		writeInt(ipAddress, 0, ip);
		try {
			return Inet4Address.getByAddress(ipAddress);
		} catch (UnknownHostException e) {
			return null;
		}
	}

	public static String ipIntToString(int ip) {
		return String.format("%s.%s.%s.%s", (ip >> 24) & 0x00FF,
				(ip >> 16) & 0x00FF, (ip >> 8) & 0x00FF, (ip & 0x00FF));
	}

	public static String ipBytesToString(byte[] ip) {
		return String.format("%s.%s.%s.%s", ip[0] & 0x00FF, ip[1] & 0x00FF, ip[2] & 0x00FF, ip[3] & 0x00FF);
	}

	public static int ipStringToInt(String ip) {
		String[] arrayStrings = ip.split("\\.");
		int r = (Integer.parseInt(arrayStrings[0]) << 24)
				| (Integer.parseInt(arrayStrings[1]) << 16)
				| (Integer.parseInt(arrayStrings[2]) << 8)
				| (Integer.parseInt(arrayStrings[3]));
		return r;
	}

	public static int readInt(byte[] data, int offset) {
		int r = ((data[offset] & 0xFF) << 24)
				| ((data[offset + 1] & 0xFF) << 16)
				| ((data[offset + 2] & 0xFF) << 8)
				| (data[offset + 3] & 0xFF);
		return r;
	}

	public static short readShort(byte[] data, int offset) {
		int r = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
		return (short) r;
	}

	public static void writeInt(byte[] data, int offset, int value) {
		data[offset] = (byte) (value >> 24);
		data[offset + 1] = (byte) (value >> 16);
		data[offset + 2] = (byte) (value >> 8);
		data[offset + 3] = (byte) value;
	}

	public static void writeShort(byte[] data, int offset, short value) {
		data[offset] = (byte) (value >> 8);
		data[offset + 1] = (byte) (value);
	}

	//计算校验和
	public static short checksum(long sum, byte[] buf, int offset, int len) {
		sum += getsum(buf, offset, len);
		while ((sum >> 16) > 0) {
			sum = (sum & 0xFFFF) + (sum >> 16);
		}
		return (short) ~sum;
	}

	public static long getsum(byte[] buf, int offset, int len) {
	//	Log.d(TAG,"getsum offset  "+offset+"  len"+len+"  length"+buf.length);
		long sum = 0;
		while (len > 1) {
			sum += readShort(buf, offset) & 0xFFFF;
			offset += 2;
			len -= 2;
		}

		if (len > 0) {
			sum += (buf[offset] & 0xFF) << 8;
		}

		return sum;
	}

	//计算IP包的校验和
	public static boolean ComputeIPChecksum(IPHeader ipHeader) {
		short oldCrc = ipHeader.getCrc();
		ipHeader.setCrc((short) 0);
		short newCrc = CommonMethods.checksum(0, ipHeader.mData, ipHeader.mOffset, ipHeader.getHeaderLength());
		ipHeader.setCrc(newCrc);
		return oldCrc == newCrc;
	}
	//计算TCP校验和
	//TCP检验和 = 整个TCP报文（不合检验和部分） +  源地址 + 目标地址 + 协议 + tcp报文长度
	public static boolean ComputeTCPChecksum(IPHeader ipHeader, TCPHeader tcpHeader) {
		ComputeIPChecksum(ipHeader);
		int ipData_len = ipHeader.getDataLength();
		if (ipData_len < 0) {
			return false;
		}

		long sum = getsum(ipHeader.mData, ipHeader.mOffset + IPHeader.offset_src_ip, 8);
		sum += ipHeader.getProtocol() & 0xFF;
		sum += ipData_len;

		short oldCrc = tcpHeader.getCrc();
		tcpHeader.setCrc((short) 0);

		short newCrc = checksum(sum, tcpHeader.mData, tcpHeader.mOffset, ipData_len);

		tcpHeader.setCrc(newCrc);
		return oldCrc == newCrc;
	}
	//计算UDP校验和
	//UDP检验和 = 整个UDP报文（不合检验和部分） +  源地址 + 目标地址 + 协议 + UDP报文长度
	public static boolean ComputeUDPChecksum(IPHeader ipHeader, UDPHeader udpHeader) {
		ComputeIPChecksum(ipHeader);
		int ipData_len = ipHeader.getDataLength();
		if (ipData_len < 0) {
			return false;
		}

		//计算伪首部和
		long sum = getsum(ipHeader.mData, ipHeader.mOffset + IPHeader.offset_src_ip, 8);
		sum += ipHeader.getProtocol() & 0xFF;
		sum += ipData_len;
		short oldCrc = udpHeader.getCrc();
		udpHeader.setCrc((short) 0);

		short newCrc = checksum(sum, udpHeader.mData, udpHeader.mOffset, ipData_len);

		udpHeader.setCrc(newCrc);
		return oldCrc == newCrc;
	}

}
