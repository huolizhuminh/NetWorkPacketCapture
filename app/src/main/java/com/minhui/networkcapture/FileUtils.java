package com.minhui.networkcapture;

import java.io.*;

/**
 * @author minhui.zhu
 *         Created by minhui.zhu on 2018/5/6.
 *         Copyright © 2017年 Oceanwing. All rights reserved.
 */

class FileUtils {
    public static void deleteFile(File file, FileFilter fileFilter) {
        if (file == null) {
            return;
        }
        if (!fileFilter.accept(file)) {
            return;
        }
        if (file.isFile()) {
            file.delete();
            return;
        }
        File[] files = file.listFiles();
        if (files == null) {
            file.delete();
            return;
        }
        for (File childFile : files) {
            deleteFile(childFile, fileFilter);
        }
        file.delete();
    }
}
