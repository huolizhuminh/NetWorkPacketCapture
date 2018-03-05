package com.zhixin.roav.vpnadapter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/2/27.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class PackageListActivity extends Activity {

    public static final String SELECT_PACKAGE = "package_select";
    private ListView packageListView;
    private List<String> packageList;
    private List<PackageInfo> installedPackages;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_package_list
        );
        packageListView = findViewById(R.id.package_list);
        packageList = new ArrayList<>();
        installedPackages = getPackageManager().getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES);
        for (PackageInfo info : installedPackages) {
            packageList.add(info.packageName);
        }
        Collections.sort(packageList);
        ArrayAdapter<String> stringArrayAdapter = new ArrayAdapter<>(this, R.layout.view_package_text, packageList);
        packageListView.setAdapter(stringArrayAdapter);
        packageListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent();
                intent.putExtra(SELECT_PACKAGE,packageList.get(position));
                setResult(RESULT_OK,intent);
                finish();
            }
        });

    }


}
