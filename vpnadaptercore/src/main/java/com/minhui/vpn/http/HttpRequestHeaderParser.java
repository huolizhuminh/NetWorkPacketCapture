package com.minhui.vpn.http;

import android.text.TextUtils;


import com.minhui.vpn.nat.NatSession;
import com.minhui.vpn.utils.AppDebug;
import com.minhui.vpn.utils.CommonMethods;
import com.minhui.vpn.utils.DebugLog;

import java.util.Locale;

/**
 * Created by zengzheying on 15/12/30.
 */
public class HttpRequestHeaderParser {

	public static void parseHttpRequestHeader(NatSession session, byte[] buffer, int offset, int count) {
		try {
			switch (buffer[offset]) {
				case 'G': //GET
				case 'H': //HEAD
				case 'P': //POST, PUT
				case 'D': //DELETE
				case 'O': //OPTIONS
				case 'T': //TRACE
				case 'C': //CONNECT
//					DebugLog.iWithTag("Debug", new String(buffer, offset, count));
					getHttpHostAndRequestUrl(session, buffer, offset, count);
					break;
				case 0x16: //SSL
					session.RemoteHost = getSNI(session, buffer, offset, count);
					break;
			}
		} catch (Exception ex) {
			if (AppDebug.IS_DEBUG) {
				ex.printStackTrace(System.err);
			}
			DebugLog.e("Error: parseHost: %s", ex);
		}
	}

	public static void getHttpHostAndRequestUrl(NatSession session, byte[] buffer, int offset, int count) {
		session.IsHttpsSession = false;
		String headerString = new String(buffer, offset, count);
		String[] headerLines = headerString.split("\\r\\n");
		String host = getHttpHost(headerLines);
		if (!TextUtils.isEmpty(host)) {
			session.RemoteHost = host;
		}
		paresRequestLine(session, headerLines[0]);
	}

	public static String getHttpHost(String[] headerLines) {

		String requestLine = headerLines[0];
		if (requestLine.startsWith("GET") || requestLine.startsWith("POST") || requestLine.startsWith("HEAD")
				|| requestLine.startsWith("OPTIONS")) {
			for (int i = 1; i < headerLines.length; i++) {
				String[] nameValueStrings = headerLines[i].split(":");
				if (nameValueStrings.length == 2) {
					String name = nameValueStrings[0].toLowerCase(Locale.ENGLISH).trim();
					String value = nameValueStrings[1].trim();
					if ("host".equals(name)) {
						return value;
					}
				}
			}
		}
		return null;
	}

	public static void paresRequestLine(NatSession session, String requestLine) {
		String[] parts = requestLine.trim().split(" ");
		if (parts.length == 3) {
			session.Method = parts[0];
			String url = parts[1];
			if (url.startsWith("/")) {
				session.RequestUrl = session.RemoteHost + url;
			} else {
				session.RequestUrl = url;
			}
		}
	}

	public static String getSNI(NatSession session, byte[] buffer, int offset, int count) {
		int limit = offset + count;
		if (count > 43 && buffer[offset] == 0x16) { //TLS Client Hello
			offset += 43; //Skip 43 byte header

			//read sessionID
			if (offset + 1 > limit) {
				return null;
			}
			int sessionIDLength = buffer[offset++] & 0xFF;
			offset += sessionIDLength;

			//read cipher suites
			if (offset + 2 > limit) {
				return null;
			}

			int cipherSuitesLength = CommonMethods.readShort(buffer, offset) & 0xFFFF;
			offset += 2;
			offset += cipherSuitesLength;

			//read Compression method.
			if (offset + 1 > limit) {
				return null;
			}
			int compressionMethodLength = buffer[offset++] & 0xFF;
			offset += compressionMethodLength;
			if (offset == limit) {
				DebugLog.w("TLS Client Hello packet doesn't contains SNI info.(offset == limit)");
				return null;
			}

			//read Extensions
			if (offset + 2 > limit) {
				return null;
			}
			int extensionsLength = CommonMethods.readShort(buffer, offset) & 0xFFFF;
			offset += 2;

			if (offset + extensionsLength > limit) {
				DebugLog.w("TLS Client Hello packet is incomplete.");
				return null;
			}

			while (offset + 4 <= limit) {
				int type0 = buffer[offset++] & 0xFF;
				int type1 = buffer[offset++] & 0xFF;
				int length = CommonMethods.readShort(buffer, offset) & 0xFFFF;
				offset += 2;

				if (type0 == 0x00 && type1 == 0x00 && length > 5) { //have SNI
					offset += 5;
					length -= 5;
					if (offset + length > limit) {
						return null;
					}
					String serverName = new String(buffer, offset, length);
					DebugLog.i("SNI: %s\n", serverName);
					session.IsHttpsSession = true;
					return serverName;
				} else {
					offset += length;
				}

			}
			DebugLog.e("TLS Client Hello packet doesn't contains Host field info.");
			return null;
		} else {
			DebugLog.e("Bad TLS Client Hello packet.");
			return null;
		}
	}

}
