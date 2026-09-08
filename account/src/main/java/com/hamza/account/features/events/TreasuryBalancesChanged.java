package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * One or more derived treasury balances changed and open balance screens must reload.
 *
 * <p>It is the cross-machine counterpart of the more precise local
 * {@link TreasuryMovementRecorded}: the remote side needs a safe invalidation signal,
 * not an invented treasury id.</p>
 */
public record TreasuryBalancesChanged() implements AppEvent {
}
