package com.minhui.vpn;

/**
 * Created by minhui.zhu on 2017/6/24.
 * Copyright © 2017年 minhui.zhu. All rights reserved.
 */

 interface CloseableRun extends Runnable {

    void closeRun();

    boolean isRunClose();
}
