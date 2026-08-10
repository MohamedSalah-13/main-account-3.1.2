package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * A unit of measure was added or edited, so anything offering units to choose
 * from is out of date. Carries nothing.
 * <p>
 * Nothing listens yet: the publisher this replaced was fired by the units screen
 * and subscribed to by no one, so the add-item screen keeps whatever units it
 * read when it opened. Kept as an event because that is the seam a listener
 * would attach to.
 */
public record UnitsChanged() implements AppEvent {
}
