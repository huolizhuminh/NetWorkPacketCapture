

package com.minhui.vpn.utils;
/**
 * Created by minhui.zhu on 2017/6/24.
 * Copyright © 2017年 minhui.zhu. All rights reserved.
 */

import java.util.LinkedHashMap;

public class MyLRUCache<K, V> extends LinkedHashMap<K, V> {
    private int maxSize;
    private transient CleanupCallback< V> callback;

    public MyLRUCache(int maxSize, CleanupCallback<V> callback) {
        super(maxSize + 1, 1, true);

        this.maxSize = maxSize;
        this.callback = callback;
    }


    @Override
    protected boolean removeEldestEntry(Entry<K, V> eldest) {
        if (size() > maxSize) {
            callback.cleanUp(eldest.getValue());
            return true;
        }
        return false;
    }

    public interface CleanupCallback<V> {
        /**
         * 清除对象
         *
         * @param eldest
         */
        void cleanUp(V v);
    }
}
