package com.hamza.account.module;

import com.hamza.account.module.delegates.DelegatesModule;

public final class AppModules {

    private static boolean registered = false;

    private AppModules() {
    }

    public static void registerModules() {
        if (registered) {
            return;
        }

        ModuleRegistry.register(new DelegatesModule());

        registered = true;
    }
}
