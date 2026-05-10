package com.hamza.account.controller.others;

import java.util.HashMap;
import java.util.Map;

public class ServiceRegistry {
    private static final Map<Class<?>, Object> services = new HashMap<>();

    public static <T> void register(Class<T> type, T instance) {
        services.put(type, instance);
    }
    
    public static <T> T get(Class<T> type) {
        return type.cast(services.get(type));
    }
}