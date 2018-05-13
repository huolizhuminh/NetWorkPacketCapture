package com.minhui.networkcapture;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.minhui.vpn.ProxyConfig;
import com.minhui.vpn.nat.NatSession;
import com.minhui.vpn.processparse.AppInfo;
import com.minhui.vpn.utils.ThreadProxy;
import com.minhui.vpn.utils.TimeFormatUtil;
import com.minhui.vpn.VPNConstants;
import com.minhui.vpn.utils.VpnServiceHelper;

import java.util.Iterator;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.minhui.vpn.VPNConstants.DEFAULT_PACKAGE_ID;
import static com.minhui.vpn.VPNConstants.VPN_SP_NAME;


/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/5/5.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class CaptureFragment extends BaseFragment {
    private static final String TAG = "CaptureFragment";

    ProxyConfig.VpnStatusListener listener = new ProxyConfig.VpnStatusListener() {

        @Override
        public void onVpnStart(Context context) {
            startTimer();
        }

        @Override
        public void onVpnEnd(Context context) {
            cancelTimer();
        }
    };
    private ScheduledExecutorService timer;
    private Handler handler;
    //private TextView summerState;
    private ConnectionAdapter connectionAdapter;
    private ListView channelList;


    private List<NatSession> allNetConnection;
    private Context context;

    @Override
    int getLayout() {
        return R.layout.fragment_capture;
    }


    @Override
    public void onDestroyView() {
        Log.d(TAG, "onDestroyView");
        super.onDestroyView();
        ProxyConfig.Instance.unregisterVpnStatusListener(listener);
        cancelTimer();
        //    connectionAdapter = null;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated");
        context = getContext();


        handler = new Handler();
        channelList = (ListView) view.findViewById(R.id.channel_list);

        channelList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (allNetConnection == null) {
                    return;
                }
                if (position > allNetConnection.size() - 1) {
                    return;
                }
                NatSession connection = allNetConnection.get(position);
                if (connection.isHttpsSession) {
                    return;
                }
                if (!NatSession.TCP.equals(connection.type)) {
                    return;
                }
                String dir = VPNConstants.DATA_DIR
                        + TimeFormatUtil.formatYYMMDDHHMMSS(connection.vpnStartTime)
                        + "/"
                        + connection.getUniqueName();
                PacketDetailActivity.startActivity(getActivity(), dir);
            }
        });
       /* LocalBroadcastManager.getInstance(getContext()).registerReceiver(vpnStateReceiver,
                new IntentFilter(LocalVPNService.BROADCAST_VPN_STATE));*/
        ProxyConfig.Instance.registerVpnStatusListener(listener);
        if (VpnServiceHelper.vpnRunningStatus()) {
            startTimer();
        }
        getDataAndRefreshView();

    }

    private void getDataAndRefreshView() {

        ThreadProxy.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                allNetConnection = VpnServiceHelper.getAllSession();
                if (allNetConnection == null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            refreshView(allNetConnection);
                        }
                    });
                    return;
                }
                Iterator<NatSession> iterator = allNetConnection.iterator();
                String packageName = context.getPackageName();

                SharedPreferences sp = getContext().getSharedPreferences(VPNConstants.VPN_SP_NAME, Context.MODE_PRIVATE);
                boolean isShowUDP = sp.getBoolean(VPNConstants.IS_UDP_SHOW, false);
                String selectPackage = sp.getString(DEFAULT_PACKAGE_ID, null);
                while (iterator.hasNext()) {
                    NatSession next = iterator.next();
                    if (next.bytesSent == 0 && next.receiveByteNum == 0) {
                        iterator.remove();
                        continue;
                    }
                    if (NatSession.UDP.equals(next.type) && !isShowUDP) {
                        iterator.remove();
                        continue;
                    }
                    AppInfo appInfo = next.appInfo;

                    if (appInfo != null) {
                        String appPackageName = appInfo.pkgs.getAt(0);
                        if (packageName.equals(appPackageName) ) {
                            iterator.remove();
                            continue;
                        }
                        if((selectPackage != null && !selectPackage.equals(appPackageName))){
                            iterator.remove();
                        }


                    }
                }
                if (handler == null) {
                    return;
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        refreshView(allNetConnection);
                    }
                });
            }
        });
    }


    private void startTimer() {
        timer = Executors.newSingleThreadScheduledExecutor();

        timer.scheduleAtFixedRate(new TimerTask() {


            @Override
            public void run() {
                getDataAndRefreshView();
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);
    }

    private void refreshView(List<NatSession> allNetConnection) {
        if (connectionAdapter == null) {
            connectionAdapter = new ConnectionAdapter(context, allNetConnection);
            channelList.setAdapter(connectionAdapter);
        } else {
            connectionAdapter.setNetConnections(allNetConnection);
            if (channelList.getAdapter() == null) {
                channelList.setAdapter(connectionAdapter);
            }
            connectionAdapter.notifyDataSetChanged();
        }


    }

    private void cancelTimer() {
        if (timer == null) {
            return;
        }
        timer.shutdownNow();
        timer = null;
    }


}

