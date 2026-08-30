package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * Money moved in or out of a treasury by hand - a deposit, a withdrawal or a
 * transfer between two of them.
 * <p>
 * It carries the treasury rather than nothing because a transfer moves two, and a
 * screen showing one of them has to know which: {@code treasuryId} is the treasury
 * whose balance changed, and a transfer publishes the event twice, once per side.
 * That is the {@code ItemSaved}/{@code ItemsChanged} split applied here - a listener
 * that has to guess what changed ends up refreshing everything or nothing.
 * <p>
 * Not a {@code DataPublisher} signal: this is something that happened to the
 * business, and it outlives the main screen. See the events section of CLAUDE.md.
 */
public record TreasuryMovementRecorded(int treasuryId) implements AppEvent {
}
