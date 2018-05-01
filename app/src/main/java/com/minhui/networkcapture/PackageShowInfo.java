package com.minhui.networkcapture;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/4/30.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class PackageShowInfo implements Parcelable {
    private static final java.lang.String NO_APP_NAME = "COM.";
    String appName;
    String packageName;
    public ApplicationInfo applicationInfo;

    public static List<PackageShowInfo> getPackageShowInfo(Context context) {
        ArrayList<PackageShowInfo> showInfos = new ArrayList<>();
        PackageManager packageManager = context.getPackageManager();
        List<PackageInfo> installedPackages = packageManager.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES);

        for (PackageInfo info : installedPackages) {
            PackageShowInfo packageShowInfo = new PackageShowInfo();
            packageShowInfo.packageName = info.packageName;

            packageShowInfo.appName = (String) info.applicationInfo.loadLabel(packageManager);

            packageShowInfo.applicationInfo = info.applicationInfo;
            showInfos.add(packageShowInfo);

        }

        Collections.sort(showInfos, new Comparator<PackageShowInfo>() {
            @Override
            public int compare(PackageShowInfo o1, PackageShowInfo o2) {
                if(o1==o2){
                    return 0;
                }
                if (o1.appName == null && o2.appName == null) {
                    return o1.packageName.toUpperCase().compareTo(o2.packageName.toUpperCase());
                }
                if (o1.appName == null) {
                    return -1;
                }
                if (o2.appName == null) {
                    return 1;
                }
                if (o1.appName.toUpperCase().startsWith(NO_APP_NAME)&&
                        !o2.appName.toUpperCase().startsWith(NO_APP_NAME)) {
                    return 1;
                }
                if (!o1.appName.toUpperCase().startsWith(NO_APP_NAME)&&
                        o2.appName.toUpperCase().startsWith(NO_APP_NAME)) {
                    return -1;
                }
                return o1.appName.toUpperCase().compareTo(o2.appName.toUpperCase());
            }
        });

        return showInfos;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.appName);
        dest.writeString(this.packageName);
        dest.writeParcelable(this.applicationInfo, flags);
    }

    public PackageShowInfo() {
    }

    protected PackageShowInfo(Parcel in) {
        this.appName = in.readString();
        this.packageName = in.readString();
        this.applicationInfo = in.readParcelable(ApplicationInfo.class.getClassLoader());
    }

    public static final Creator<PackageShowInfo> CREATOR = new Creator<PackageShowInfo>() {
        @Override
        public PackageShowInfo createFromParcel(Parcel source) {
            return new PackageShowInfo(source);
        }

        @Override
        public PackageShowInfo[] newArray(int size) {
            return new PackageShowInfo[size];
        }
    };
}
