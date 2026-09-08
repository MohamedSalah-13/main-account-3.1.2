package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * One or more warehouse balances changed and balance-driven screens must reload.
 *
 * <p>The event deliberately carries no item or warehouse id. A stock count or a
 * transfer may move many rows, and a remote machine must be able to reconstruct the
 * event exactly without fabricating a partial model.</p>
 */
public record StockBalancesChanged() implements AppEvent {
}
