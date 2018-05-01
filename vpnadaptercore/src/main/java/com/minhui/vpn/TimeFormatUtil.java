package com.minhui.vpn;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/5/1.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class TimeFormatUtil {

    public static String formatHHMMSSMM(long time) {
        String formart = "HH:mm:ss:s";
        Date date = new Date(time);
        DateFormat dateFormat = new SimpleDateFormat(formart);
        return dateFormat.format(date);
    }
}
