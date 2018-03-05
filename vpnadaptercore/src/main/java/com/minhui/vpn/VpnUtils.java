package com.minhui.vpn;

import android.os.Build;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

/**
 * creator: charles date: 2017/8/21
 */

public class VpnUtils {
    private static final String NEED_WRITE_SETTING_VERSION = "6.0";
    private static final String NEED_WRITE_SETTING_VERSION_1 = "6.0.1";
    private static final String MODEL = "KIW-L24";
    private static final String TAG = VpnUtils.class.getSimpleName();

    /**
     * 用于判断6。0版本write setting权限
     *
     * @return
     */
    public static boolean needCheckWriteSetting() {
        String ver = Build.VERSION.RELEASE;
        String model = Build.MODEL;
//        VPNLog.d(TAG,"check write setting, model:"+model+",ver:"+ver);
        if(ver.equals(NEED_WRITE_SETTING_VERSION)){
            return true;
        }else if(NEED_WRITE_SETTING_VERSION_1.equals(ver)&&MODEL.equals(model)){
            return true;
        }
        return false;
    }

    public static String getString(ByteBuffer buffer) {

        Charset charset = null;

        CharsetDecoder decoder = null;

        CharBuffer charBuffer = null;

        try {

            charset = Charset.forName("UTF-8");

            decoder = charset.newDecoder();

            //用这个的话，只能输出来一次结果，第二次显示为空
            buffer.flip();
            charBuffer = decoder.decode(buffer);
            buffer.position(0);
            return charBuffer.toString();

        } catch (Exception ex) {

            ex.printStackTrace();

            return "error";

        }

    }
}
