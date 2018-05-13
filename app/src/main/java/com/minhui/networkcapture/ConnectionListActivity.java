package com.minhui.networkcapture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.minhui.vpn.nat.NatSession;
import com.minhui.vpn.utils.ACache;
import com.minhui.vpn.processparse.AppInfo;
import com.minhui.vpn.utils.ThreadProxy;
import com.minhui.vpn.utils.TimeFormatUtil;
import com.minhui.vpn.VPNConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/5/6.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class ConnectionListActivity extends Activity {

    private RecyclerView recyclerView;
    public static final String FILE_DIRNAME = "file_dirname";
    private String fileDir;
    private ArrayList<NatSession> baseNetSessions;
    private Handler handler;
    private ConnectionAdapter connectionAdapter;
    private PackageManager packageManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection_list);
        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        recyclerView = findViewById(R.id.recycle_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(ConnectionListActivity.this));
        fileDir = getIntent().getStringExtra(FILE_DIRNAME);
        handler = new Handler();
        getDataAndRefreshView();

        packageManager = getPackageManager();

    }

    private void getDataAndRefreshView() {
        ThreadProxy.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                baseNetSessions = new ArrayList<>();
                File file = new File(fileDir);
                ACache aCache = ACache.get(file);
                String[] list = file.list();
                if (list == null || list.length == 0) {
                    refreshView();
                    return;
                }
                SharedPreferences sp = getSharedPreferences(VPNConstants.VPN_SP_NAME, Context.MODE_PRIVATE);
                boolean isShowUDP = sp.getBoolean(VPNConstants.IS_UDP_SHOW, false);
                for (String fileName : list) {

                    NatSession netConnection = (NatSession) aCache.getAsObject(fileName);
                    if (NatSession.UDP.equals(netConnection.type) && !isShowUDP) {
                        continue;
                    }
                    baseNetSessions.add(netConnection);
                }
                Collections.sort(baseNetSessions, new NatSession.NatSesionComparator());

                refreshView();

            }
        });

    }

    private void refreshView() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (baseNetSessions == null || baseNetSessions.size() == 0) {
                    Toast.makeText(ConnectionListActivity.this, getString(R.string.no_data), Toast.LENGTH_SHORT).show();
                    finish();
                }
                connectionAdapter = new ConnectionAdapter();
                recyclerView.setAdapter(connectionAdapter);
            }
        });

    }

    public static void openActivity(Activity activity, String dir) {
        Intent intent = new Intent(activity, ConnectionListActivity.class);
        intent.putExtra(FILE_DIRNAME, dir);
        activity.startActivity(intent);

    }

    class ConnectionAdapter extends RecyclerView.Adapter {

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View inflate = View.inflate(ConnectionListActivity.this, R.layout.item_connection, null);
            return new ConnectionHolder(inflate);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, final int position) {
            final NatSession connection = baseNetSessions.get(position);
            ConnectionHolder connectionHolder = (ConnectionHolder) holder;
            Drawable icon;
            if (connection.getAppInfo() != null) {
                icon = AppInfo.getIcon(getApplication(), connection.getAppInfo().pkgs.getAt(0));
            } else {
                icon = getResources().getDrawable(R.drawable.sym_def_app_icon);
            }

            connectionHolder.icon.setImageDrawable(icon);
            if (connection.getAppInfo() != null) {
                connectionHolder.processName.setText(connection.getAppInfo().leaderAppName);
            } else {
                connectionHolder.processName.setText(getString(R.string.unknow));
            }

            connectionHolder.hostName.setVisibility(connection.getRequestUrl() != null || connection.getRemoteHost() != null ?
                    View.VISIBLE : View.GONE);
            connectionHolder.hostName.setText(connection.getRequestUrl() != null ? connection.getRequestUrl() : connection.getRemoteHost());
            connectionHolder.netState.setText(connection.getIpAndPort());
            connectionHolder.isSSL.setVisibility(connection.isHttpsSession() ? View.VISIBLE : View.GONE);


            connectionHolder.refreshTime.setText(TimeFormatUtil.formatHHMMSSMM(connection.getRefreshTime()));
            int sumByte = (int) (connection.getBytesSent() + connection.getReceiveByteNum());

            String showSum;
            if (sumByte > 1000000) {
                showSum = String.valueOf((int) (sumByte / 1000000.0 + 0.5)) + "mb";
            } else if (sumByte > 1000) {
                showSum = String.valueOf((int) (sumByte / 1000.0 + 0.5)) + "kb";
            } else {
                showSum = String.valueOf(sumByte) + "b";
            }

            connectionHolder.size.setText(showSum);
            connectionHolder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (baseNetSessions.get(position).isHttpsSession()) {
                        return;
                    }
                    startPacketDetailActivity(baseNetSessions.get(position));


                }
            });


        }

        @Override
        public int getItemCount() {
            return baseNetSessions == null ? 0 : baseNetSessions.size();
        }

        class ConnectionHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView processName;
            TextView netState;
            TextView refreshTime;
            TextView size;
            TextView isSSL;
            TextView hostName;

            public ConnectionHolder(View view) {
                super(view);
                icon = (ImageView) view.findViewById(R.id.select_icon);
                refreshTime = (TextView) view.findViewById(R.id.refresh_time);
                size = (TextView) view.findViewById(R.id.net_size);
                isSSL = (TextView) view.findViewById(R.id.is_ssl);
                processName = (TextView) view.findViewById(R.id.app_name);
                netState = (TextView) view.findViewById(R.id.net_state);
                hostName = (TextView) view.findViewById(R.id.url);
            }
        }
    }

    private void startPacketDetailActivity(NatSession connection) {
        String dir = VPNConstants.DATA_DIR
                + TimeFormatUtil.formatYYMMDDHHMMSS(connection.getVpnStartTime())
                + "/"
                + connection.getUniqueName();
        PacketDetailActivity.startActivity(this, dir);

    }
}
