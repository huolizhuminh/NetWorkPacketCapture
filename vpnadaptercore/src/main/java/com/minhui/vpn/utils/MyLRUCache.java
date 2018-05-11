

package com.minhui.vpn.utils;
/**
 * Created by minhui.zhu on 2017/6/24.
 * Copyright © 2017年 minhui.zhu. All rights reserved.
 */

import java.util.LinkedHashMap;

public class MyLRUCache<K, V> extends LinkedHashMap<K, V> {
    private int maxSize;
    private transient CleanupCallback<K, V> callback;

    public MyLRUCache(int maxSize, CleanupCallback<K, V> callback) {
        super(maxSize + 1, 1, true);

        this.maxSize = maxSize;
        this.callback = callback;
    }

    @Override
    public boolean removeEldestEntry(Entry<K, V> eldest) {
        if (size() > maxSize) {
            callback.cleanUp(eldest);
            return true;
        }
        return false;
    }

    public interface CleanupCallback<K, V> {
        /**
         * 清除对象
         *
         * @param eldest
         */
        void cleanUp(Entry<K, V> eldest);
    }
}
