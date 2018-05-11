package com.minhui.vpn.dns;


import com.minhui.vpn.utils.CommonMethods;

/**
 * Created by zengzheying on 15/12/29.
 */
public class ResourcePointer {

	static final short offset_Domain = 0;
	static final short offset_Type = 2;
	static final short offset_Class = 4;
	static final int offset_TTL = 6;
	static final int offset_DataLength = 10;
	static final int offset_IP = 12;

	byte[] mData;
	int mOffset;

	public ResourcePointer(byte[] data, int offset) {
		mData = data;
		mOffset = offset;
	}

	public void setDomain(short value) {
		CommonMethods.writeInt(mData, mOffset + offset_Domain, value);
	}

	public short getType() {
		return CommonMethods.readShort(mData, mOffset + offset_Type);
	}

	public void setType(short value) {
		CommonMethods.writeShort(mData, mOffset + offset_Type, value);
	}

	public short getClass(short value) {
		return CommonMethods.readShort(mData, mOffset + offset_Class);
	}

	public void setClass(short value) {
		CommonMethods.writeShort(mData, mOffset + offset_Class, value);
	}

	public int getTTL() {
		return CommonMethods.readInt(mData, mOffset + offset_TTL);
	}

	public void setTTL(int value) {
		CommonMethods.writeInt(mData, mOffset + offset_TTL, value);
	}

	public short getDataLength() {
		return CommonMethods.readShort(mData, mOffset + offset_DataLength);
	}

	public void setDataLength(short value) {
		CommonMethods.writeShort(mData, mOffset + offset_DataLength, value);
	}

	public int getIP() {
		return CommonMethods.readInt(mData, mOffset + offset_IP);
	}

	public void setIP(int value) {
		CommonMethods.writeInt(mData, mOffset + offset_IP, value);
	}
}
