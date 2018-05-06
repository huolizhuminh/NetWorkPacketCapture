package com.minhui.vpn;

import android.os.Environment;

/**
 * Created by minhui.zhu on 2017/6/24.
 * Copyright © 2017年 minhui.zhu. All rights reserved.
 */

public interface VPNConstants {
    int BUFFER_SIZE = 2560;
    int MAX_PAYLOAD_SIZE = 2520;
    String BASE_DIR = Environment.getExternalStorageDirectory() + "/VpnCapture/Conversation/";
}
