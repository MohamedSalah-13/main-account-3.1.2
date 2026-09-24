package com.hamza.account.features.pricing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The rows and totals of the two reports of phase A - plain values, nothing binds to them. */
public final class TierReports {

    private TierReports() {
    }

    /**
     * An item with no price on at least one tier in use.
     *
     * @param prices the item's price on each tier, tier 1 first
     */
    public record MissingPrice(int itemId, String barcode, String name, List<Double> prices,
                               List<Integer> missingTiers) {

        public MissingPrice {
            prices = List.copyOf(prices);
            missingTiers = List.copyOf(missingTiers);
        }

        public double price(int tier) {
            return prices.get(PriceTiers.orFirst(tier) - 1);
        }
    }

    public record MissingPage(List<MissingPrice> rows, int total) {
    }

    /** A sales line priced below its list, who sold it and what it gave away. */
    public record BelowList(int invoiceNumber, LocalDate date, String customer, String user, String item,
                            String unit, double quantity, BigDecimal listPrice, BigDecimal price,
                            BigDecimal given, Integer tierId) {
    }

    /** The whole filtered set's figures, whatever page is on screen. */
    public record BelowListSummary(int lines, int invoices, BigDecimal given) {

        public static final BelowListSummary NONE = new BelowListSummary(0, 0, BigDecimal.ZERO);
    }

    public record BelowListPage(List<BelowList> rows, BelowListSummary summary) {
    }
}
