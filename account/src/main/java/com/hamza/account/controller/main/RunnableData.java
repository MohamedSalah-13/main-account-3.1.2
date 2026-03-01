package com.hamza.account.controller.main;

public interface RunnableData {
    default void defaultMethod() {
    }

    default String name() {
        return "default name";
    }
}
