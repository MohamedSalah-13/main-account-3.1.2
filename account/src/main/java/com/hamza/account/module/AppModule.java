package com.hamza.account.module;

public interface AppModule {

    String name();

    AppFeature feature();

    default boolean isEnabled() {
        return FeatureManager.isEnabled(feature());
    }

    default void initialize(ModuleContext context) throws Exception {
    }

    default void registerMenus(ModuleContext context) throws Exception {
    }

    default void registerToolbarButtons(ModuleContext context) throws Exception {
    }

    default void registerPermissions(ModuleContext context) throws Exception {
    }

    default void shutdown(ModuleContext context) throws Exception {
    }
}
