package com.minhui.networkcapture;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.minhui.vpn.AppInfo;
import com.minhui.vpn.BaseNetConnection;
import com.minhui.vpn.TimeFormatUtil;

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
    private List<BaseNetConnection> netConnections;

    ConnectionAdapter(Context context, List<BaseNetConnection> netConnections) {
        this.context = context;
        this.netConnections = netConnections;
    }

    public void setNetConnections(List<BaseNetConnection> netConnections) {
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
        Holder holder;
        if (convertView == null) {
            convertView = View.inflate(context, R.layout.item_packet, null);
            holder = new Holder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }
        BaseNetConnection connection = netConnections.get(position);
        if (connection.appInfo != null) {

            holder.processName.setText(connection.appInfo.leaderAppName);
            if (connection.appInfo.pkgs != null) {
                holder.icon.setImageDrawable(AppInfo.getIcon(context, connection.appInfo.pkgs.getAt(0)));
            }
        }
        holder.isSSL.setVisibility(View.GONE);
        holder.hostName.setText(null);
        holder.hostName.setVisibility(View.GONE);
        if (BaseNetConnection.TCP.equals(connection.type)) {
            if (connection.isSSL) {
                holder.isSSL.setVisibility(View.VISIBLE);
            }

            if (connection.url != null) {
                holder.hostName.setText(connection.url);
            } else {
                holder.hostName.setText(connection.hostName);
            }
            if(connection.url!=null||connection.hostName!=null){
                holder.hostName.setVisibility(View.VISIBLE);
            }
        }

        holder.netState.setText(connection.ipAndPort);
        holder.refreshTime.setText(TimeFormatUtil.formatHHMMSSMM(connection.refreshTime));
        int sumByte = (int) (connection.sendByteNum + connection.receiveByteNum);

        String showSum;
        if (sumByte > 1000000) {
            showSum = String.valueOf((int) (sumByte / 1000000.0 + 0.5)) + "mb";
        } else if (sumByte > 1000) {
            showSum = String.valueOf((int) (sumByte / 1000.0 + 0.5)) + "kb";
        } else {
            showSum = String.valueOf(sumByte) + "b";
        }

        holder.size.setText(showSum);

        return convertView;
    }

    class Holder {
        ImageView icon;
        TextView processName;
        TextView netState;
        TextView refreshTime;
        TextView size;
        TextView isSSL;
        TextView hostName;
        View baseView;

        Holder(View view) {
            baseView = view;
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
