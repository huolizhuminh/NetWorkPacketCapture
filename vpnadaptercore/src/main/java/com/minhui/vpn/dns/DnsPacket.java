package com.minhui.vpn.dns;

import java.nio.ByteBuffer;

/**
 * Created by zengzheying on 15/12/29.
 */
public class DnsPacket {

	/**
	 * DNS数据报格式
	 * <p/>
	 * 说明一下：并不是所有DNS报文都有以上各个部分的。图中标示的“12字节”为DNS首部，这部分肯定都会有
	 * 首部下面的是正文部分，其中查询问题部分也都会有。
	 * 除此之外，回答、授权和额外信息部分是只出现在DNS应答报文中的，而这三部分又都采用资源记录（Recource Record）的相同格式
	 * ０　　　　　　　　　　　１５　　１６　　　　　　　　　　　　３１
	 * ｜－－－－－－－－－－－－－－－－－－－－－－－－－－－－－｜　　－－
	 * ｜          标识          ｜           标志           ｜　　  ｜
	 * ｜－－－－－－－－－－－－－－－－－－－－－－－－－－－－－｜     ｜
	 * ｜         问题数         ｜        资源记录数         ｜　　１２字节
	 * ｜－－－－－－－－－－－－－－－－－－－－－－－－－－－－－｜    　｜
	 * ｜　    授权资源记录数     ｜      额外资源记录数        ｜     ｜
	 * ｜－－－－－－－－－－－－－－－－－－－－－－－－－－－－－｜　　－－
	 * ｜　　　　　　　　      查询问题                        ｜
	 * ｜－－－－－－－－－－－－－－－－－－－－－－－－－－－－－｜
	 * ｜                      回答                         ｜
	 * ｜　             （资源记录数可变）                    ｜
	 * ｜－－－－－－－－－－－－－－－－－－－－－－－－－－－－－｜
	 * ｜                      授权                         ｜
	 * ｜               （资源记录数可变）                    ｜
	 * ｜－－－－－－－－－－－－－－－－－－－－－－－－－－－－－｜
	 * ｜                  　额外信息                        ｜
	 * ｜               （资源记录数可变）                    ｜
	 * ｜－－－－－－－－－－－－－－－－－－－－－－－－－－－－－｜
	 */

	public DnsHeader Header;
	public Question[] Questions;
	public Resource[] Resources;
	public Resource[] AResources;
	public Resource[] EResources;

	public int Size;

	public static DnsPacket fromBytes(ByteBuffer buffer) {
		if (buffer.limit() < 12) {
			return null;
		}
		if (buffer.limit() > 512) {
			return null;
		}

		DnsPacket packet = new DnsPacket();
		packet.Size = buffer.limit();
		packet.Header = DnsHeader.fromBytes(buffer);

		if (packet.Header.QuestionCount > 2 || packet.Header.ResourceCount > 50
				|| packet.Header.AResourceCount > 50
				|| packet.Header.EResourceCount > 50) {
			return null;
		}

		packet.Questions = new Question[packet.Header.QuestionCount];
		packet.Resources = new Resource[packet.Header.ResourceCount];
		packet.AResources = new Resource[packet.Header.AResourceCount];
		packet.EResources = new Resource[packet.Header.EResourceCount];

		for (int i = 0; i < packet.Questions.length; i++) {
			packet.Questions[i] = Question.fromBytes(buffer);
		}

		for (int i = 0; i < packet.Resources.length; i++) {
			packet.Resources[i] = Resource.fromBytes(buffer);
		}

		for (int i = 0; i < packet.AResources.length; i++) {
			packet.AResources[i] = Resource.fromBytes(buffer);
		}

		for (int i = 0; i < packet.EResources.length; i++) {
			packet.EResources[i] = Resource.fromBytes(buffer);
		}

		return packet;
	}

	public static String readDomain(ByteBuffer buffer, int dnsHeaderOffset) {
		StringBuilder sb = new StringBuilder();
		int len = 0;
		while (buffer.hasRemaining() && (len = (buffer.get() & 0xFF)) > 0) {
			if ((len & 0xC0) == 0xC0) { //pointer 高2位为11表示是指针。如：1100 0000
				// 指针的取值是前一字节的后6位加后一字节的8位共14的值
				int pointer = buffer.get() & 0xFF; //低8位
				pointer |= (len & 0x3F) << 8;

				ByteBuffer newBuffer = ByteBuffer.wrap(buffer.array(), dnsHeaderOffset + pointer, dnsHeaderOffset +
						buffer.limit());
//				ByteBuffer newBuffer = ByteBuffer.wrap(buffer.array(), dnsHeaderOffset + pointer, buffer.limit() -
//						(dnsHeaderOffset + pointer));
				sb.append(readDomain(newBuffer, dnsHeaderOffset));
				return sb.toString();
			} else {
				while (len > 0 && buffer.hasRemaining()) {
					sb.append((char) (buffer.get() & 0xFF));
					len--;
				}
				sb.append(".");
			}
		}

		if (len == 0 && sb.length() > 0) {
			sb.deleteCharAt(sb.length() - 1); //去掉末尾的点（.）
		}
		return sb.toString();
	}

	public static void writeDomain(String domain, ByteBuffer buffer) {
		if (domain == null || "".equals(domain.trim())) {
			buffer.put((byte) 0);
			return;
		}

		String[] arr = domain.split("\\.");
		for (String item : arr) {
			if (arr.length > 1) {
				buffer.put((byte) item.length());
			}

			for (int i = 0; i < item.length(); i++) {
				buffer.put((byte) item.codePointAt(i));
			}
		}
	}

	public void toBytes(ByteBuffer buffer) {
		Header.QuestionCount = 0;
		Header.ResourceCount = 0;
		Header.AResourceCount = 0;
		Header.EResourceCount = 0;

		if (Questions != null) {
			Header.QuestionCount = (short) Questions.length;
		}
		if (Resources != null) {
			Header.ResourceCount = (short) Resources.length;
		}
		if (AResources != null) {
			Header.AResourceCount = (short) AResources.length;
		}
		if (EResources != null) {
			Header.EResourceCount = (short) EResources.length;
		}

		this.Header.toBytes(buffer);

		for (int i = 0; i < Header.QuestionCount; i++) {
			this.Questions[i].toBytes(buffer);
		}

		for (int i = 0; i < Header.ResourceCount; i++) {
			this.Resources[i].toBytes(buffer);
		}

		for (int i = 0; i < Header.AResourceCount; i++) {
			this.AResources[i].toBytes(buffer);
		}

		for (int i = 0; i < Header.EResourceCount; i++) {
			this.EResources[i].toBytes(buffer);
		}
	}
}
