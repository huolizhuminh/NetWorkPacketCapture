package com.minhui.vpn.tcpip;

import com.minhui.vpn.utils.CommonMethods;

/**
 * Created by zengzheying on 15/12/28.
 */
public class UDPHeader {
	/**
	 * UDP数据报格式
	 * 头部长度：8字节
	 * ｜－－－－－－－－－－－－－－－－－－－－－－－－－－－－－｜
	 * ｜  １６位源端口号         ｜   １６位目的端口号        ｜
	 * ｜－－－－－－－－－－－－－－－－－－－－－－－－－－－－－｜
	 * ｜  １６位ＵＤＰ长度       ｜   １６位ＵＤＰ检验和       ｜
	 * ｜－－－－－－－－－－－－－－－－－－－－－－－－－－－－－｜
	 * ｜                  数据（如果有）                    ｜
	 * ｜－－－－－－－－－－－－－－－－－－－－－－－－－－－－－｜
	 **/

	static final short offset_src_port = 0; // 源端口
	static final short offset_dest_port = 2; //目的端口
	static final short offset_tlen = 4; //数据报长度
	static final short offset_crc = 6; //校验和

	public byte[] mData;
	public int mOffset;

	public UDPHeader(byte[] data, int offset) {
		mData = data;
		mOffset = offset;
	}

	public short getSourcePort() {
		return CommonMethods.readShort(mData, mOffset + offset_src_port);
	}

	public void setSourcePort(short value) {
		CommonMethods.writeShort(mData, mOffset + offset_src_port, value);
	}

	public short getDestinationPort() {
		return CommonMethods.readShort(mData, mOffset + offset_dest_port);
	}

	public void setDestinationPort(short value) {
		CommonMethods.writeShort(mData, mOffset + offset_dest_port, value);
	}

	public int getTotalLength() {
		return CommonMethods.readShort(mData, mOffset + offset_tlen) & 0xFFFF;
	}

	public void setTotalLength(int value) {
		CommonMethods.writeShort(mData, mOffset + offset_tlen, (short) value);
	}

	public short getCrc() {
		return CommonMethods.readShort(mData, mOffset + offset_crc);
	}

	public void setCrc(short value) {
		CommonMethods.writeShort(mData, mOffset + offset_crc, value);
	}

	@Override
	public String toString() {
		return String.format("%d->%d", getSourcePort() & 0xFFFF, getDestinationPort() & 0xFFFF);
	}
}
