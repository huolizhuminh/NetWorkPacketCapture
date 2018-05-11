package com.minhui.vpn;

import java.nio.channels.SelectionKey;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/5/11.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public interface KeyHandler {
    void onKeyReady(SelectionKey key);
}
