package com.minhui.vpn.dns;

import java.nio.ByteBuffer;

/**
 * Created by zengzheying on 15/12/29.
 */
public class Question {

	/**
	 * 查询字段
	 * <p/>
	 * 0            15 16           31
	 * |-----------------------------|
	 * |            查询名            |
	 * |           *不定长度          |
	 * |-----------------------------|
	 * |    查询类型    |   查询类     |
	 * |-----------------------------|
	 * <p/>
	 * 查询名： 长度不定，一般为要查询的域名（也会有IP的时候，即反向查询）。此部分由一个或者多个标示符序列组成
	 * 每个标示符以首字节的计数值来说明标示符长度，每个名字以0结束。计数字节数必须是0-63之间。该字段
	 * 无需填充字节。例如查询为gemini.tuc.noao.edu的话，查询名字段如下：
	 * <p/>
	 * |---------------------------------------|
	 * |6|g|e|m|n|i|3|t|u|c|4|n|o|a|o|3|e|d|u|0|
	 * |---------------------------------------|
	 * |           |       |         |       |
	 * 计数         计数     计数      计数    计数
	 * <p/>
	 * 查询类： 通常查询类型为A(由名字获得IP地址)或PTR(获得IP地址对应的域名)，类型列表如下
	 * <p/>
	 * 类型   助记符     说明
	 * 1      A         IPv4地址
	 * 2      NS        名字服务器
	 * 5      CNAME     规范名称定义主机的正式名字的别名
	 * 6      SOA       开始授权标记一个区的开始
	 * 11     WKS       熟知服务定义主机提供的网络服务
	 * 12     PTR       指针把IP地址转化为域名
	 * 13     HINFO     主机信息给出主机使用的硬件和操作系统的表述
	 * 15     MX        邮件交换把邮件改变路由送到邮件服务器
	 * 28     AAAA      IPv6地址
	 * 252    AXFR      传送整个区的请求
	 * 255    ANY       对所有记录的请求
	 * <p/>
	 * 查询类： 通常为1，表示Internet数据
	 */

	public String Domain;
	public short Type;
	public short Class;

	private int mOffset;
	private int mLength;

	public static Question fromBytes(ByteBuffer buffer) {
		Question q = new Question();
		q.mOffset = buffer.arrayOffset() + buffer.position();
		q.Domain = DnsPacket.readDomain(buffer, buffer.arrayOffset());
		q.Type = buffer.getShort();
		q.Class = buffer.getShort();
		q.mLength = buffer.arrayOffset() + buffer.position() - q.mOffset;
		return q;
	}

	public int Offset() {
		return mOffset;
	}

	public int Length() {
		return mLength;
	}

	public void toBytes(ByteBuffer buffer) {
		this.mOffset = buffer.position();
		DnsPacket.writeDomain(this.Domain, buffer);
		buffer.putShort(this.Type);
		buffer.putShort(this.Class);
		this.mLength = buffer.position() - this.mOffset;
	}

}
