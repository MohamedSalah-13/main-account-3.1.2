package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/** Payload-free invalidation used when another workstation changes a shift. */
public record ShiftsChanged() implements AppEvent {
}
