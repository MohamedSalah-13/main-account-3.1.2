package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * A main or sub group was added, edited or deleted, so the combo boxes and lists
 * offering groups are out of date.
 */
public record GroupsChanged(GroupLevel level) implements AppEvent {
}
