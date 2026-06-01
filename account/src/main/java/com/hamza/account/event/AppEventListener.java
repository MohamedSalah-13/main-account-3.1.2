package com.hamza.account.event;

@FunctionalInterface
public interface AppEventListener {

    void onEvent(AppEvent event);
}
