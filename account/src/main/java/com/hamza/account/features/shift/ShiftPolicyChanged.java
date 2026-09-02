package com.hamza.account.features.shift;

import com.hamza.controlsfx.observer.AppEvent;

public record ShiftPolicyChanged(ShiftPolicy policy) implements AppEvent {
}
