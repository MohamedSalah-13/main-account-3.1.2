package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * A warehouse was added, renamed or removed, so anything offering a warehouse to
 * pick from is out of date. Carries nothing.
 * <p>
 * Every screen with a warehouse combo builds its list once, when {@code ItemsButtons}
 * first constructs that screen's controller - which happens once per session, well
 * before a user has necessarily created a second warehouse. Without this, a warehouse
 * created after that point is invisible to every already-built screen: its balance
 * cannot be selected on the inventory sheet, and an invoice already saved against it
 * cannot re-select it when reopened for edit.
 */
public record StocksChanged() implements AppEvent {
}
