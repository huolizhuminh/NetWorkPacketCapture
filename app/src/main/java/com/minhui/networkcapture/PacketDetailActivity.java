package com.minhui.networkcapture;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.minhui.vpn.VPNConstants;
import com.minhui.vpn.utils.SaveDataFileParser;
import com.minhui.vpn.utils.ThreadProxy;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/5/3.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class PacketDetailActivity extends Activity {
    public static final String CONVERSATION_DATA = "conversation_data";
    private static final String TAG = "PacketDetailActivity";
    private ListView list;
    private String dir;
    private SharedPreferences sp;
    private List<SaveDataFileParser.ShowData> showDataList;
    private ProgressBar pg;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.acitivity_packet_detail);
        list = (ListView) findViewById(R.id.detail_list);
        dir = getIntent().getStringExtra(CONVERSATION_DATA);
        pg = findViewById(R.id.pg);
        sp = getSharedPreferences(AppConstants.DATA_SAVE, MODE_PRIVATE);
        sp.edit().putBoolean(AppConstants.HAS_FULL_USE_APP, true).apply();
        refreshView();
    }

    private void refreshView() {
        ThreadProxy.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                File file = new File(dir);
                File[] files = file.listFiles();
                if (files == null || files.length == 0) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            finish();

                        }
                    });
                    return;
                }
                List<File> filesList = new ArrayList<>();
                for (File childFile : files) {
                    filesList.add(childFile);
                }
                Collections.sort(filesList, new Comparator<File>() {
                    @Override
                    public int compare(File o1, File o2) {
                        return (int) (o1.lastModified() - o2.lastModified());
                    }
                });
                showDataList = new ArrayList<>();
                for (File childFile : filesList) {
                    SaveDataFileParser.ShowData showData = SaveDataFileParser.parseSaveFile(childFile);
                    if (showData != null) {
                        showDataList.add(showData);
                    }

                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        DetailAdapter detailAdapter = new DetailAdapter();
                        list.setAdapter(detailAdapter);
                        pg.setVisibility(View.GONE);
                    }
                });

            }
        });
    }


    public static void startActivity(Activity context, String dir) {
        Intent intent = new Intent(context, PacketDetailActivity.class);
        intent.putExtra(CONVERSATION_DATA, dir);
        context.startActivity(intent);
    }


    class DetailAdapter extends BaseAdapter {
        private int requestBg = getResources().getColor(R.color.colorAccent_light);
        private int requestTextColor = getResources().getColor(R.color.colorAccent);
        private int responseBg = getResources().getColor(R.color.colorPrimaryDark_light);
        private int responseTextColor = getResources().getColor(R.color.colorPrimaryDark);

        @Override
        public int getCount() {
            return showDataList == null ? 0 : showDataList.size();
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
            SaveDataFileParser.ShowData showData = showDataList.get(position);
            holder.bodyImage.setVisibility(showData.getBodyImage() == null ? View.GONE : View.VISIBLE);
            holder.bodyData.setVisibility((TextUtils.isEmpty(showData.getBodyStr())) ? View.GONE : View.VISIBLE);
            holder.itemView.setBackgroundColor(showData.isRequest() ? requestBg : responseBg);
            holder.headData.setTextColor(showData.isRequest() ? requestTextColor : responseTextColor);
            holder.bodyData.setTextColor(showData.isRequest() ? requestTextColor : responseTextColor);
            holder.headData.setText(showData.getHeadStr());
            holder.headTitle.setText(getString(showData.isRequest() ? R.string.request_head : R.string.response_head));
            if (showData.getBodyStr() != null) {
                String showStr = " " + showData.getBodyStr();
                //如果数组太长了的话在部分手机中可能会报错

                holder.bodyData.setText(showStr);


            } else {
                holder.bodyData.setText("");

            }
            if (showData.getBodyImage() != null) {
                holder.bodyImage.setImageBitmap(showData.getBodyImage());
            }
            holder.bodyTitle.setVisibility((showData.isBodyNull() ? View.GONE : View.VISIBLE));
            holder.bodyTitle.setText(getString(showData.isRequest() ? R.string.request_body : R.string.response_body));
            return convertView;
        }

        class Holder {
            TextView headData;
            TextView bodyData;
            TextView bodyTitle;
            ImageView bodyImage;
            View itemView;
            TextView headTitle;

            Holder(View view) {
                itemView = view.findViewById(R.id.container);
                headData = (TextView) view.findViewById(R.id.conversation_head_text);
                bodyData = view.findViewById(R.id.conversation_body_text);
                bodyImage = view.findViewById(R.id.conversation_body_im);
                bodyTitle = view.findViewById(R.id.body_title);
                headTitle = view.findViewById(R.id.head_title);
            }
        }
    }


}
