package com.minhui.vpn.dns;


import com.minhui.vpn.utils.CommonMethods;

import java.nio.ByteBuffer;

/**
 * Created by zengzheying on 15/12/29.
 */
public class DnsHeader {

	/**
	 * DNS数据包头部
	 * <p/>
	 * ０　　　　　　　　　　　１５　　１６　　　　　　　　　　　　３１
	 * ｜－－－－－－－－－－－－－－－－－－－－－－－－－－－－－｜　　－－
	 * ｜          标识          ｜           标志          ｜　　  ｜
	 * ｜－－－－－－－－－－－－－－－－－－－－－－－－－－－－－｜     ｜
	 * ｜         问题数         ｜        资源记录数        ｜　　１２字节
	 * ｜－－－－－－－－－－－－－－－－－－－－－－－－－－－－－｜    　｜
	 * ｜　    授权资源记录数     ｜      额外资源记录数       ｜     ｜
	 * ｜－－－－－－－－－－－－－－－－－－－－－－－－－－－－－｜　　－－
	 */

	static final short offset_ID = 0;
	static final short offset_Flags = 2;
	static final short offset_QuestionCount = 4;
	static final short offset_ResourceCount = 6;
	static final short offset_AResourceCount = 8;
	static final short offset_EResourceCount = 10;

	public short ID;
	public DnsFlag flags;
	public short QuestionCount;
	public short ResourceCount;
	public short AResourceCount;
	public short EResourceCount;
	public byte[] mData;
	public int mOffset;

	public DnsHeader(byte[] data, int offset) {
		mData = data;
		mOffset = offset;
	}

	public static DnsHeader fromBytes(ByteBuffer buffer) {
		DnsHeader header = new DnsHeader(buffer.array(), buffer.arrayOffset() + buffer.position());
		header.ID = buffer.getShort();
		header.flags = DnsFlag.Parse(buffer.getShort());
		header.QuestionCount = buffer.getShort();
		header.ResourceCount = buffer.getShort();
		header.AResourceCount = buffer.getShort();
		header.EResourceCount = buffer.getShort();
		return header;
	}

	public void toBytes(ByteBuffer buffer) {
		buffer.putShort(this.ID);
		buffer.putShort(this.flags.ToShort());
		buffer.putShort(this.QuestionCount);
		buffer.putShort(this.ResourceCount);
		buffer.putShort(this.AResourceCount);
		buffer.putShort(this.EResourceCount);
	}

	public short getID() {
		return CommonMethods.readShort(mData, mOffset + offset_ID);
	}

	public void setID(short value) {
		CommonMethods.writeShort(mData, mOffset + offset_ID, value);
	}

	public short getFlags() {
		return CommonMethods.readShort(mData, mOffset + offset_Flags);
	}

	public void setFlags(short value) {
		CommonMethods.writeShort(mData, mOffset + offset_Flags, value);
	}

	public short getQuestionCount() {
		return CommonMethods.readShort(mData, mOffset + offset_QuestionCount);
	}

	public void setQuestionCount(short value) {
		CommonMethods.writeShort(mData, mOffset + offset_QuestionCount, value);
	}

	public short getResourceCount() {
		return CommonMethods.readShort(mData, mOffset + offset_ResourceCount);
	}

	public void setResourceCount(short value) {
		CommonMethods.writeShort(mData, mOffset + offset_ResourceCount, value);
	}

	public short getAResourceCount() {
		return CommonMethods.readShort(mData, mOffset + offset_AResourceCount);
	}

	public void setAResourceCount(short value) {
		CommonMethods.writeShort(mData, mOffset + offset_AResourceCount, value);
	}

	public short getEResourceCount() {
		return CommonMethods.readShort(mData, mOffset + offset_EResourceCount);
	}

	public void setEResourceCount(short value) {
		CommonMethods.writeShort(mData, mOffset + offset_EResourceCount, value);
	}
}
