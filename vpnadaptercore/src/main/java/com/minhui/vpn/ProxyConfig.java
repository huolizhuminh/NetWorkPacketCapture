package com.minhui.vpn;


import android.content.Context;


import com.minhui.vpn.utils.CommonMethods;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by zengzheying on 15/12/28.
 */
public class ProxyConfig {

	public static final ProxyConfig Instance = new ProxyConfig();
	String mSessionName;
	int mMtu;
	private List<VpnStatusListener> mVpnStatusListeners=new ArrayList<>();


	private ProxyConfig() {


	}



	public String getSessionName() {
		if (mSessionName == null) {
			mSessionName = "Easy Firewall";
		}
		return mSessionName;
	}

	public int getMTU() {
		if (mMtu > 1400 && mMtu <= 20000) {
			return mMtu;
		} else {
			return 20000;
		}
	}



	public void registerVpnStatusListener(VpnStatusListener vpnStatusListener) {
		mVpnStatusListeners.add(vpnStatusListener);
	}
	public void unregisterVpnStatusListener(VpnStatusListener vpnStatusListener) {
		mVpnStatusListeners.remove(vpnStatusListener);
	}

	public void onVpnStart(Context context) {
		VpnStatusListener[] vpnStatusListeners = new VpnStatusListener[mVpnStatusListeners.size()];
		mVpnStatusListeners.toArray(vpnStatusListeners);
		for(VpnStatusListener listener :vpnStatusListeners){
			listener.onVpnStart(context);
		}
	}


	public void onVpnEnd(Context context) {
		VpnStatusListener[] vpnStatusListeners = new VpnStatusListener[mVpnStatusListeners.size()];
		mVpnStatusListeners.toArray(vpnStatusListeners);
		for(VpnStatusListener listener :vpnStatusListeners){
			listener.onVpnEnd(context);
		}
	}

	public IPAddress getDefaultLocalIP() {
		return new IPAddress("10.8.0.2", 32);
	}

	public interface VpnStatusListener {
		void onVpnStart(Context context);

		void onVpnEnd(Context context);
	}

	public static class IPAddress {
		public final String Address;
		public final int PrefixLength;

		public IPAddress(String address, int prefixLength) {
			Address = address;
			PrefixLength = prefixLength;
		}

		public IPAddress(String ipAddressString) {
			String[] arrStrings = ipAddressString.split("/");
			String address = arrStrings[0];
			int prefixLength = 32;
			if (arrStrings.length > 1) {
				prefixLength = Integer.parseInt(arrStrings[1]);
			}

			this.Address = address;
			this.PrefixLength = prefixLength;
		}

		@Override
		public boolean equals(Object o) {
			if (o == null || !(o instanceof IPAddress)) {
				return false;
			} else {
				return this.toString().equals(o.toString());
			}
		}

		@Override
		public String toString() {
			return String.format("%s/%d", Address, PrefixLength);
		}
	}
}
