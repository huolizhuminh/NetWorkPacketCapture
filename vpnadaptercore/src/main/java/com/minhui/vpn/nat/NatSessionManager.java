package com.minhui.vpn.nat;

import android.util.SparseArray;

import com.minhui.vpn.utils.CommonMethods;

/**
 * Created by zengzheying on 15/12/29.
 * NAT管理对象
 */
public class NatSessionManager {

	static final int MAX_SESSION_COUNT = 64; //会话保存的最大个数
	static final long SESSION_TIME_OUT_NS = 60 * 1000 * 1000 * 1000L; //会话保存时间
	static final SparseArray<NatSession> Sessions = new SparseArray<>();

	/**
	 * 通过本地端口获取会话信息
	 *
	 * @param portKey 本地端口
	 * @return 会话信息
	 */
	public static NatSession getSession(int portKey) {
		return Sessions.get(portKey);
	}

	/**
	 * 获取会话个数
	 *
	 * @return 会话个数
	 */
	public static int getSessionCount() {
		return Sessions.size();
	}

	/**
	 * 清除过期的会话
	 */
	static void clearExpiredSessions() {
		long now = System.nanoTime();
		for (int i = Sessions.size() - 1; i >= 0; i--) {
			NatSession session = Sessions.valueAt(i);
			if (now - session.LastNanoTime > SESSION_TIME_OUT_NS) {
				Sessions.removeAt(i);
			}
		}
	}

	/**
	 * 创建会话
	 *
	 * @param portKey    源端口
	 * @param remoteIP   远程ip
	 * @param remotePort 远程端口
	 * @return NatSession对象
	 */
	public static NatSession createSession(int portKey, int remoteIP, short remotePort) {
		if (Sessions.size() > MAX_SESSION_COUNT) {
			clearExpiredSessions(); //清除过期的会话
		}

		NatSession session = new NatSession();
		session.LastNanoTime = System.nanoTime();
		session.RemoteIP = remoteIP;
		session.RemotePort = remotePort;



		if (session.RemoteHost == null) {
			session.RemoteHost = CommonMethods.ipIntToString(remoteIP);
		}

		Sessions.put(portKey, session);
		return session;
	}

}
