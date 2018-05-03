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

package com.minhui.networkcapture;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Pair;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.minhui.vpn.BaseNetConnection;
import com.minhui.vpn.ConversationData;
import com.minhui.vpn.LocalVPNService;
import com.minhui.vpn.VPNConnectManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class LocalVPN extends Activity {
    private static final int VPN_REQUEST_CODE = 101;
    private static final int REQUEST_PACKAGE = 103;
    private static final String DATA_SAVE = "saveData";
    private static final String DEFAULT_PACKAGE_ID = "default_package_id";
    private static final String DEFAULT_PACAGE_NAME = "default_package_name";

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
    //private TextView summerState;
    private ConnectionAdapter connectionAdapter;
    private ListView channelList;
    private SharedPreferences sharedPreferences;
    private String selectPackage;
    private String selectName;

    private List<BaseNetConnection> allNetConnection;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_vpn);

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
      //  summerState = findViewById(R.id.summer_state);
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
        selectName=sharedPreferences.getString(DEFAULT_PACAGE_NAME,null);
        packageId.setText(selectName!=null?selectName:
                selectPackage!=null?selectPackage:getString(R.string.all));
        vpnButton.setEnabled(true);
        channelList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(allNetConnection==null){
                    return;
                }
                if(position>allNetConnection.size()-1){
                    return;
                }
                BaseNetConnection connection = allNetConnection.get(position);
                if(!BaseNetConnection.TCP.equals(connection.type)){
                    return;
                }
                if(connection.hostName==null){
                    return;

                }
                ArrayList<ConversationData> conversation = connection.getConversation();
                if(conversation.isEmpty()){
                    return;
                }
                PacketDetailActivity.startActivity(LocalVPN.this,conversation);
            }
        });
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
            if (selectPackage != null) {
                intent.putExtra(LocalVPNService.SELECT_PACKAGE_ID, selectPackage.trim());
            }
            startService(intent);
            startTimer();
            VPNConnectManager.getInstance().resetNum();
        } else if (requestCode == REQUEST_PACKAGE && resultCode == RESULT_OK) {
            PackageShowInfo showInfo = (PackageShowInfo) data.getParcelableExtra(PackageListActivity.SELECT_PACKAGE);
            if(showInfo==null){
                selectPackage=null;
                selectName = null;
            }else {
                selectPackage=showInfo.packageName;
                selectName=showInfo.appName;
            }
            packageId.setText(selectName!=null?selectName:
                    selectPackage!=null?selectPackage:getString(R.string.all));
            vpnButton.setEnabled(true);
            sharedPreferences.edit().putString(DEFAULT_PACKAGE_ID, selectPackage)
            .putString(DEFAULT_PACAGE_NAME,selectName).apply();
        }
    }

    private void startTimer() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {


            @Override
            public void run() {
                allNetConnection = VPNConnectManager.getInstance().getAllNetConnection();

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        refreshView(allNetConnection);
                    }
                });
            }
        }, 1000, 1000);
    }

    private void refreshView(List<BaseNetConnection> allNetConnection) {
        VPNConnectManager vpnConnectManager = VPNConnectManager.getInstance();

        if (allNetConnection == null || allNetConnection.isEmpty()) {
            return;
        }

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


    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

    private void closeVpn() {
        Intent intent = new Intent(this, LocalVPNService.class);
        intent.setAction(LocalVPNService.ACTION_CLOSE_VPN);
        startService(intent);
        cancelTimer();
    }


}
