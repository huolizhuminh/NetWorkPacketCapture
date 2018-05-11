package com.minhui.vpn.dns;

import java.nio.ByteBuffer;

/**
 * Created by zengzheying on 15/12/29.
 */
public class Resource {
	/**
	 * 资源记录（RR）：回答字段，授权字段和附加信息字段（额外信息）均采用这种格式
	 * <p/>
	 * 0                   15  16                     31
	 * |----------------------------------------------|
	 * |                    域名                       |
	 * |               *2字节或不定长                   |
	 * |----------------------------------------------|
	 * |     类型              |         类            |
	 * |----------------------------------------------|
	 * |                    生存时间                   |
	 * |----------------------------------------------|
	 * |      资源数据长度       |                      |
	 * |-----------------------|                      |
	 * |                    资源数据                   |
	 * |                    *长度不定                  |
	 * |----------------------------------------------|
	 * <p/>
	 * 域        名：  	记录中资源数据对应的名字，它的格式和查询名字段格式相同。当报文中的域名重复出现的时候，
	 * 就需要使用2字节的偏移指针来替换。例如，在资源记录中，域名通常是查询问题部分的域名的重复
	 * 就需要使用指针指向查询问题部分的域名。即2字节的指针，最前面的两个高位是 11，用于识别指针，
	 * 其他14位从报文开始处计数（从0开始），指出该报文中相应的字节数。注意，DNS报文的第一个字节
	 * 是字节0，第二个字节是字节1，以此类推。一般响应报文中，资源部分的域名通常是指针C00C
	 * （1100 0000 0000 1100，12正好是首部区域的长度），刚好指向请求部分的域名
	 * 类        型：  	记录的类型，见查询字段。
	 * A记录       -> Name是主机名，Value是该主机名的IP地址，因此，一条类型为A的资源记录提供了标准
	 * 的主机名到IP地址的映射。
	 * NS记录      -> Name是域（如baidu.com），Value是知道如何获得该域中主机IP地址的权威DNS服务器
	 * 的主机名（如dns.baidu.com），这个记录常用于沿着查询链进一步路由DNS查询
	 * CNAME记录   -> Name是主机别名，Value是主机别名对应的规范主机名，该记录能够向请求主机提供一个
	 * 主机名对应的规范主机名
	 * MX记录      -> Name是邮件服务器别名，Value是邮件服务器别名的规范主机名。通过MX记录，一个公司
	 * 的邮件服务器和其他服务器可以使用相同的别名
	 * * 有着 复杂主机名 的主机能拥有多个别名，前者称为规范主机名，后者称为主机别名（便于记忆）
	 * <p/>
	 * 类         ：  	对于Internet信息，它总是IN
	 * 生 存 时 间 ：  	用于指示该记录的稳定程度，极为稳定的信息会被分配一个很大的值（如86400，一天的秒数）。该字段表示资源
	 * 记录的生命周期（以秒为单位），一般用于当地址解析程序取出资源记录后决定保存及使用缓存数据的时间
	 * 资源数据长度 ： 	（2字节）表示数据资源的长度（以字节为单位，如果资源数据为IP则为0004）。
	 * 资 源 数 据 ：   	该字段是可变长字段，表示按查询要求返回的相关资源记录的数据。
	 */

	public String Domain;
	public short Type;
	public short Class;
	public int TTL;
	public short DataLangth;
	public byte[] Data;

	private int mOffset;
	private int mLength;

	public static Resource fromBytes(ByteBuffer buffer) {
		Resource r = new Resource();
		r.mOffset = buffer.arrayOffset() + buffer.position();
		r.Domain = DnsPacket.readDomain(buffer, buffer.arrayOffset());
		r.Type = buffer.getShort();
		r.Class = buffer.getShort();
		r.TTL = buffer.getInt();
		r.DataLangth = buffer.getShort();
		r.Data = new byte[r.DataLangth & 0xFFFF];
		buffer.get(r.Data);
		r.mLength = buffer.arrayOffset() + buffer.position() - r.mOffset;
		return r;
	}

	public int Offset() {
		return mOffset;
	}

	public int Length() {
		return mLength;
	}

	public void toBytes(ByteBuffer buffer) {
		if (this.Data == null) {
			this.Data = new byte[0];
		}
		this.DataLangth = (short) this.Data.length;

		this.mOffset = buffer.position();
		DnsPacket.writeDomain(this.Domain, buffer);
		buffer.putShort(this.Type);
		buffer.putShort(this.Class);
		buffer.putInt(this.TTL);

		buffer.putShort(this.DataLangth);
		buffer.put(this.Data);
		this.mLength = buffer.position() - this.mOffset;
	}

}
