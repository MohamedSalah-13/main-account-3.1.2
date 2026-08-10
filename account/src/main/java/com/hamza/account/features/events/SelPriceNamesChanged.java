package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

import java.util.Map;

/**
 * The three sel-price tiers were renamed in settings; {@code names} maps each
 * tier's id to its new name, and the items table relabels its columns with it.
 */
public record SelPriceNamesChanged(Map<Integer, String> names) implements AppEvent {
}
