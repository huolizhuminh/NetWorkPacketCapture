package com.minhui.networkcapture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.minhui.vpn.ConversationData;

import java.util.ArrayList;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/5/3.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class PacketDetailActivity extends Activity {
    public static final String CONVERSATION_DATA = "conversation_data";
    private ListView list;
    private ArrayList<ConversationData> conversationDatas;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.acitivity_packet_detail);
        list = findViewById(R.id.detail_list);
        conversationDatas = getIntent().getParcelableArrayListExtra(CONVERSATION_DATA);
        DetailAdapter detailAdapter = new DetailAdapter();
        list.setAdapter(detailAdapter);
    }

    public static void startActivity(Activity context, ArrayList<ConversationData> data) {
        Intent intent = new Intent(context, PacketDetailActivity.class);
        intent.putParcelableArrayListExtra(CONVERSATION_DATA, data);
        context.startActivity(intent);
    }


    class DetailAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return conversationDatas.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder holder;
            if (convertView == null) {
                convertView = View.inflate(PacketDetailActivity.this, R.layout.item_conversation, null);
                holder = new Holder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (Holder) convertView.getTag();
            }
            ConversationData conversationData = conversationDatas.get(position);
            holder.data.setText(conversationData.getData());
            holder.data.setTextColor(conversationData.isRequest() ? Color.RED : Color.GREEN);
            return convertView;
        }

        class Holder {
            TextView data;

            Holder(View view) {
                data = view.findViewById(R.id.conversation_text);
            }
        }
    }
}
