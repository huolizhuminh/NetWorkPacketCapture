package com.minhui.vpn.processparse;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.io.Serializable;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/4/30.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class PackageNames implements Parcelable,Serializable {
    public static final Creator<PackageNames> CREATOR = new Creator<PackageNames>() {
        @Override
        public PackageNames createFromParcel(Parcel in) {
            return new PackageNames(in);
        }

        @Override
        public PackageNames[] newArray(int size) {
            return new PackageNames[size];
        }
    };
    public final String[] pkgs;

    public static PackageNames newInstance(String[] pkgs) {
        return new PackageNames(pkgs);
    }

    public static PackageNames newInstanceFromCommaList(String pkgList) {
        return newInstance(pkgList.split(","));
    }

    public String getAt(int i) {
        if (this.pkgs.length > i) {
            return this.pkgs[i];
        }
        return null;
    }

    public String getCommaJoinedString() {
        return TextUtils.join(",", this.pkgs);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.pkgs.length);
        dest.writeStringArray(this.pkgs);
    }

    protected PackageNames(String[] pkgs) {
        this.pkgs = pkgs;
    }

    protected PackageNames(Parcel in) {
        this.pkgs = new String[in.readInt()];
        in.readStringArray(this.pkgs);
    }
}
