package com.hamza.account;

import com.hamza.account.config.AspectConfig;
import com.hamza.account.security.cache.PermissionCacheManager;
import com.hamza.account.view.DownLoadApplication;

public class Main {

    public static void main(String[] args) throws Exception {

        // تهيئة Aspects
        AspectConfig.initialize();

        DownLoadApplication.main(args);

        // طباعة إحصائيات Cache عند الإغلاق
        Runtime.getRuntime().addShutdownHook(new Thread(() -> PermissionCacheManager.printStats()));
    }

}
