/*
** Copyright 2015, Mohamed Naufal
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package com.zhixin.roav.vpnadapter;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.zhixin.roav.vpnadaptercore.LocalVPNService;
import com.zhixin.roav.vpnadaptercore.LocalVpnInit;
import com.zhixin.roav.vpnadaptercore.NetConnection;
import com.zhixin.roav.vpnadaptercore.VPNConnectManager;
import com.zhixin.roav.vpnadaptercore.VpnUtils;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class LocalVPN extends Activity {
    private static final int VPN_REQUEST_CODE = 101;
    private static final int REQUEST_WRITE = 102;
    private static final int REQUEST_PACKAGE = 103;
    private static final String DATA_SAVE = "saveData";
    private static final String DEFAULT_PACKAGE_ID = "default_package_id";

    private boolean waitingForVPNStart;

    private BroadcastReceiver vpnStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            vpnButton.setText(LocalVPNService.isRunning() ? "stopvpn" : "start vpn");
        }
    };
    private Button vpnButton;
    private TextView packageId;
    private Timer timer;
    private Handler handler;
    private TextView summerState;
    private ConnectionAdapter connectionAdapter;
    private ListView channelList;
    private SharedPreferences sharedPreferences;
    private String selectPackage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_vpn);
        LocalVpnInit.init(getString(R.string.app_name), getApplicationContext());
        vpnButton = (Button) findViewById(R.id.vpn);
        vpnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (LocalVPNService.isRunning()) {
                    closeVpn();
                } else {
                    startVPN();
                }
            }
        });
        packageId = findViewById(R.id.package_id);
        summerState = findViewById(R.id.summer_state);
        findViewById(R.id.select_package).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LocalVPN.this, PackageListActivity.class);
                startActivityForResult(intent, REQUEST_PACKAGE);
            }
        });
        waitingForVPNStart = false;
        LocalBroadcastManager.getInstance(this).registerReceiver(vpnStateReceiver,
                new IntentFilter(LocalVPNService.BROADCAST_VPN_STATE));

        handler = new Handler();
        channelList = findViewById(R.id.channel_list);
        sharedPreferences = getSharedPreferences(DATA_SAVE, MODE_PRIVATE);
        selectPackage = sharedPreferences.getString(DEFAULT_PACKAGE_ID, null);
        if (selectPackage != null) {
            packageId.setText(selectPackage);
            vpnButton.setEnabled(true);
        }
    }

    private void startSetPermissionDialog() {
        new AlertDialog
                .Builder(this)
                .setTitle("network permission")
                .setPositiveButton("setting", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startWrittingSetting();
                    }
                })
                .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                }).show();
    }

    private void startWrittingSetting() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityForResult(intent, REQUEST_WRITE);
    }

    private void startVPN() {

        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent != null)
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
        else
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            waitingForVPNStart = true;
            Intent intent = new Intent(this, LocalVPNService.class);
            intent.setAction(LocalVPNService.ACTION_START_VPN);
            intent.putExtra(LocalVPNService.SELECT_PACKAGE_ID, packageId.getText().toString().trim());
            startService(intent);
            startTimer();
            VPNConnectManager.getInstance().resetNum();
        } else if (requestCode == REQUEST_WRITE && resultCode == RESULT_OK) {
            VPNConnectManager.getInstance().initNetWork();
        } else if (requestCode == REQUEST_PACKAGE && resultCode == RESULT_OK) {
            selectPackage = data.getStringExtra(PackageListActivity.SELECT_PACKAGE);
            packageId.setText(selectPackage);
            vpnButton.setEnabled(true);
            sharedPreferences.edit().putString(DEFAULT_PACKAGE_ID, selectPackage).apply();
        }
    }

    private void startTimer() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        refreshView();
                    }
                });
            }
        }, 1000, 1000);
    }

    private void refreshView() {
        VPNConnectManager vpnConnectManager = VPNConnectManager.getInstance();
        List<NetConnection> allNetConnection = vpnConnectManager.getAllNetConnection();
        if (allNetConnection == null || allNetConnection.isEmpty()) {
            return;
        }
        String summerText = "TotalSendPacket" + vpnConnectManager.getTotalSendPacket()
                + ",TotalSendByte:" + vpnConnectManager.getTotalSendNum()
                + ",TotalReceivePacket:" + vpnConnectManager.getTotalReceivePacket()
                + ",TotalReceiveByte:" + vpnConnectManager.getTotalReceiveByteNum();
        summerState.setText(summerText);
        if (connectionAdapter == null) {
            connectionAdapter = new ConnectionAdapter(this, allNetConnection);
            channelList.setAdapter(connectionAdapter);
        } else {
            connectionAdapter.setNetConnections(allNetConnection);
            connectionAdapter.notifyDataSetChanged();
        }


    }

    private void cancelTimer() {
        if (timer == null) {
            return;
        }
        timer.purge();
        timer.cancel();
        timer = null;
    }


    @Override
    protected void onStart() {
        super.onStart();
        //初始化ip白名单
        if (VpnUtils.needCheckWriteSetting() && !Settings.System.canWrite(this)) {
            startSetPermissionDialog();
        } else {
            VPNConnectManager.getInstance().initNetWork();
        }


    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        VPNConnectManager.getInstance().unRegisterNetWorkCallBacks();

    }

    private void closeVpn() {
        Intent intent = new Intent(this, LocalVPNService.class);
        intent.setAction(LocalVPNService.ACTION_CLOSE_VPN);
        startService(intent);
        cancelTimer();
    }


}
