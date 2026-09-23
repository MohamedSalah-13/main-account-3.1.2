package com.hamza.account.features.currency;

import java.util.List;

/**
 * What recording a batch of rates did (docs/currency-plan.md ق-٩, §12).
 *
 * @param recorded the currencies whose rate was written
 * @param kept     the currencies left alone because their day already had a rate when the row was locked -
 *                 recorded at another till while the dialog was open, say. Never replaced.
 */
public record RecordedRates(List<Integer> recorded, List<Integer> kept) {

    public RecordedRates {
        recorded = List.copyOf(recorded);
        kept = List.copyOf(kept);
    }
}
