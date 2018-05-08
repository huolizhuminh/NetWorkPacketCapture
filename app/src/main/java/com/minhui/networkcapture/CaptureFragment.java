package com.minhui.networkcapture;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.minhui.vpn.ACache;
import com.minhui.vpn.AppInfo;
import com.minhui.vpn.BaseNetConnection;
import com.minhui.vpn.LocalVPNService;
import com.minhui.vpn.PortHostService;
import com.minhui.vpn.ThreadProxy;
import com.minhui.vpn.TimeFormatUtil;
import com.minhui.vpn.VPNConnectManager;
import com.minhui.vpn.VPNConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/5/5.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class CaptureFragment extends BaseFragment {
    private static final String TAG = "CaptureFragment";
    private BroadcastReceiver vpnStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (LocalVPNService.isRunning()) {
                startTimer();
            } else {
                cancelTimer();
            }
        }
    };
    private ScheduledExecutorService timer;
    private Handler handler;
    //private TextView summerState;
    private ConnectionAdapter connectionAdapter;
    private ListView channelList;


    private List<BaseNetConnection> allNetConnection;
    private Context context;

    @Override
    int getLayout() {
        return R.layout.fragment_capture;
    }


    @Override
    public void onDestroyView() {
        Log.d(TAG, "onDestroyView");
        super.onDestroyView();
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(vpnStateReceiver);
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
                BaseNetConnection connection = allNetConnection.get(position);
                if (connection.isSSL()) {
                    return;
                }
                if (!BaseNetConnection.TCP.equals(connection.getType())) {
                    return;
                }
                String dir = VPNConstants.DATA_DIR
                        + TimeFormatUtil.formatYYMMDDHHMMSS(connection.getVpnStartTime())
                        + "/"
                        + connection.getUniqueName();
                PacketDetailActivity.startActivity(getActivity(), dir);
            }
        });
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(vpnStateReceiver,
                new IntentFilter(LocalVPNService.BROADCAST_VPN_STATE));
        if (LocalVPNService.isRunning()) {
            startTimer();
        }
        getDataAndRefreshView();

    }

    private void getDataAndRefreshView() {

        ThreadProxy.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                allNetConnection = VPNConnectManager.getInstance().getAllConn();
                if (allNetConnection == null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            refreshView(allNetConnection);
                        }
                    });
                    return;
                }
                Iterator<BaseNetConnection> iterator = allNetConnection.iterator();
                String packageName = getContext().getPackageName();
                while (iterator.hasNext()) {
                    BaseNetConnection next = iterator.next();
                    if (BaseNetConnection.UDP.equals(next.getType())) {
                        iterator.remove();
                        continue;
                    }
                    AppInfo appInfo = next.getAppInfo();
                    if (appInfo != null
                            && appInfo.pkgs.getAt(0) != null
                            && packageName.equals(appInfo.pkgs.getAt(0))) {
                        iterator.remove();
                    }
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

    private void refreshView(List<BaseNetConnection> allNetConnection) {
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

