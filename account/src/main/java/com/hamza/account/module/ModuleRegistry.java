package com.hamza.account.module;

import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Log4j2
public final class ModuleRegistry {

    private static final List<AppModule> MODULES = new ArrayList<>();

    private ModuleRegistry() {
    }

    public static void register(AppModule module) {
        MODULES.add(module);
    }

    public static List<AppModule> getModules() {
        return Collections.unmodifiableList(MODULES);
    }

    public static void initializeModules(ModuleContext context) {
        for (AppModule module : MODULES) {
            if (!module.isEnabled()) {
                log.info("Module disabled: {}", module.name());
                continue;
            }

            try {
                log.info("Initializing module: {}", module.name());
                module.initialize(context);
                module.registerPermissions(context);
                module.registerMenus(context);
                module.registerToolbarButtons(context);
            } catch (Exception e) {
                log.error("Error initializing module: {}", module.name(), e);
            }
        }
    }

    public static void shutdownModules(ModuleContext context) {
        for (AppModule module : MODULES) {
            if (!module.isEnabled()) {
                continue;
            }

            try {
                module.shutdown(context);
            } catch (Exception e) {
                log.error("Error shutting down module: {}", module.name(), e);
            }
        }
    }
}
