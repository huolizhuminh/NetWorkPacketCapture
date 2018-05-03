package com.minhui.vpn;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/5/3.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class ConversationData implements Serializable,Parcelable {
    private String data;
    private boolean isRequest;

    public ConversationData(boolean isRequest,  String data ) {
        this.data = data;
        this.isRequest = isRequest;
    }

    public String getData() {
        return data;
    }

    public boolean isRequest() {
        return isRequest;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.data);
        dest.writeByte(this.isRequest ? (byte) 1 : (byte) 0);
    }

    protected ConversationData(Parcel in) {
        this.data = in.readString();
        this.isRequest = in.readByte() != 0;
    }

    public static final Creator<ConversationData> CREATOR = new Creator<ConversationData>() {
        @Override
        public ConversationData createFromParcel(Parcel source) {
            return new ConversationData(source);
        }

        @Override
        public ConversationData[] newArray(int size) {
            return new ConversationData[size];
        }
    };
}
