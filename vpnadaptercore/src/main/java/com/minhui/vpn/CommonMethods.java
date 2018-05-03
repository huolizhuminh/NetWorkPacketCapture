package com.minhui.vpn;


import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by zengzheying on 15/12/28.
 */
public class CommonMethods {

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

}
