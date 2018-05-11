package com.minhui.vpn.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/5/1.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class TimeFormatUtil {
    private static DateFormat HHMMSSSFormat = new SimpleDateFormat("HH:mm:ss:s", Locale.getDefault());
    private static DateFormat formatYYMMDDHHMMSSFormat=new SimpleDateFormat("yyyy:MM:dd_HH:mm:ss:s", Locale.getDefault());
    public static String formatHHMMSSMM(long time) {
        Date date = new Date(time);
        return HHMMSSSFormat.format(date);
    }
    public static String formatYYMMDDHHMMSS(long time) {
        Date date = new Date(time);
        return formatYYMMDDHHMMSSFormat.format(date);
    }
}
