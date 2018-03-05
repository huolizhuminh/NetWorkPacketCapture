

package com.zhixin.roav.vpnadaptercore;
/**
 * Created by minhui.zhu on 2017/6/24.
 * Copyright © 2017年 Oceanwing. All rights reserved.
 */
import java.util.LinkedHashMap;

class MyLRUCache<K, V> extends LinkedHashMap<K, V> {
   private int maxSize;
   private transient  CleanupCallback<K,V> callback;

    MyLRUCache(int maxSize, CleanupCallback<K,V> callback) {
       super(maxSize + 1, 1, true);

       this.maxSize = maxSize;
       this.callback = callback;
   }

   @Override
   protected boolean removeEldestEntry(Entry<K, V> eldest) {
       if (size() > maxSize) {
           callback.cleanup(eldest);
           return true;
       }
       return false;
   }

   interface CleanupCallback<K, V> {
       void cleanup(Entry<K, V> eldest);
   }
}
