package com.hamza.account.event;

public record AppEvent(
        EventType type,
        EventAction action,
        Integer id,
        Object payload
) {
}
