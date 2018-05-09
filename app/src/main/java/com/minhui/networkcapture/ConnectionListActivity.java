package com.minhui.networkcapture;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.PersistableBundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.minhui.vpn.ACache;
import com.minhui.vpn.AppInfo;
import com.minhui.vpn.BaseNetConnection;
import com.minhui.vpn.ConversationData;
import com.minhui.vpn.ThreadProxy;
import com.minhui.vpn.TimeFormatUtil;
import com.minhui.vpn.VPNConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Vector;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/5/6.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class ConnectionListActivity extends Activity {

    private RecyclerView recyclerView;
    public static final String FILE_DIRNAME = "file_dirname";
    private String fileDir;
    private ArrayList<BaseNetConnection> baseNetConnections;
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
                baseNetConnections = new ArrayList<>();
                File file = new File(fileDir);
                ACache aCache = ACache.get(file);
                String[] list = file.list();
                if (list == null || list.length == 0) {
                    refreshView();
                    return;
                }
                for (String fileName : list) {
                    BaseNetConnection netConnection = (BaseNetConnection) aCache.getAsObject(fileName);
                    baseNetConnections.add(netConnection);
                }
                Collections.sort(baseNetConnections, new BaseNetConnection.NetConnectionComparator());

                refreshView();

            }
        });

    }

    private void refreshView() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (baseNetConnections == null || baseNetConnections.size() == 0) {
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
            final BaseNetConnection connection = baseNetConnections.get(position);
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

            connectionHolder.hostName.setVisibility(connection.getUrl() != null || connection.getHostName() != null ?
                    View.VISIBLE : View.GONE);
            connectionHolder.hostName.setText(connection.getUrl() != null ? connection.getUrl() : connection.getHostName());
            connectionHolder.netState.setText(connection.getIpAndPort());
            connectionHolder.isSSL.setVisibility(connection.isSSL() ? View.VISIBLE : View.GONE);


            connectionHolder.refreshTime.setText(TimeFormatUtil.formatHHMMSSMM(connection.getRefreshTime()));
            int sumByte = (int) (connection.getSendByteNum() + connection.getReceiveByteNum());

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
                    if (baseNetConnections.get(position).isSSL()) {
                        return;
                    }
                    startPacketDetailActivity(baseNetConnections.get(position));


                }
            });


        }

        @Override
        public int getItemCount() {
            return baseNetConnections == null ? 0 : baseNetConnections.size();
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

    private void startPacketDetailActivity(BaseNetConnection connection) {
        String dir = VPNConstants.DATA_DIR
                + TimeFormatUtil.formatYYMMDDHHMMSS(connection.getVpnStartTime())
                + "/"
                + connection.getUniqueName();
        PacketDetailActivity.startActivity(this, dir);

    }
}
