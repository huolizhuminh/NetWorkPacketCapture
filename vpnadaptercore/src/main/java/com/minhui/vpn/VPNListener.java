package com.minhui.vpn;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/4/4.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public interface VPNListener {
    void onPacketSend(Packet packet);

    void onPacketReceive(Packet packet);
}
