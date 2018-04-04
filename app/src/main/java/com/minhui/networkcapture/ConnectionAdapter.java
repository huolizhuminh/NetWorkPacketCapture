package com.minhui.networkcapture;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.minhui.vpn.NetConnection;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/2/28.
 *         Copyright © 2017年 minhui.zhu. All rights reserved.
 */

public class ConnectionAdapter extends BaseAdapter {
    private final Context context;
    private List<NetConnection> netConnections;
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:MM:SS");

    ConnectionAdapter(Context context, List<NetConnection> netConnections) {
        this.context = context;
        this.netConnections = netConnections;
    }

    public void setNetConnections(List<NetConnection> netConnections) {
        this.netConnections = netConnections;
    }

    @Override
    public int getCount() {
        return netConnections.size();
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = View.inflate(context, R.layout.view_package_text, null);
        }
        TextView item = convertView.findViewById(R.id.package_item);
        NetConnection connection = netConnections.get(position);
        StringBuilder stringBuilder = new StringBuilder();
        Date date = new Date(connection.refreshTime);
        String time = simpleDateFormat.format(date);
        //   String time = "" + date.getMinutes() + ":" + date.getSeconds();

        String itemString = stringBuilder
                .append(connection.ipAndPort)
                .append(",refreshTime:")
                .append(time)
                .append(",sendPacket:")
                .append(connection.sendPacketNum)
                .append(",sendNum:")
                .append(connection.sendNum)
                .append(",receivePacket:")
                .append(connection.receivePacketNum)
                .append(",receiveNum:")
                .append(connection.receiveNum)
                .append(",hostName")
                .append(connection.hostName)
                .toString();
        item.setText(itemString);
        return convertView;
    }


}
