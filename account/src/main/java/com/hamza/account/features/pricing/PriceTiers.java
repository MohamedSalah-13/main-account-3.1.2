package com.hamza.account.features.pricing;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.ItemsUnitsModel;

import java.util.List;

/**
 * What a price tier is to an item and to a unit: the one place that knows tier 2 is
 * {@code sel_price2}. It used to be said by a {@code switch} in four classes - the two sales
 * invoices, the price check and {@code ItemUnits} - and written by a fifth, so a fourth tier would
 * have been five edits that had to agree (docs/pricing-and-offers-plan.md ق-س١).
 * <p>
 * The tiers are three and fixed: {@code type_price} holds their names and whether each is in use,
 * {@code items.sel_price1..3} and {@code items_units.sel_price..3} their prices. An id this class
 * does not know - the zero a supplier's screen passes - reads as tier 1, as every one of the old
 * switches did.
 */
public final class PriceTiers {

    /** The tier every item carries a price on, and the one a missing price falls back to. */
    public static final int FIRST = 1;

    /** Every tier, in order. */
    public static final List<Integer> IDS = List.of(1, 2, 3);

    private PriceTiers() {
    }

    public static boolean exists(int tierId) {
        return tierId >= 1 && tierId <= IDS.size();
    }

    /** {@code tierId} if it names a tier, else {@link #FIRST}: what the old switches' default did. */
    public static int orFirst(int tierId) {
        return exists(tierId) ? tierId : FIRST;
    }

    /** The item's own price on a tier, per base unit - zero where none was entered. */
    public static double itemPrice(ItemsModel item, int tierId) {
        if (item == null) {
            return 0;
        }
        return switch (orFirst(tierId)) {
            case 2 -> item.getSelPrice2();
            case 3 -> item.getSelPrice3();
            default -> item.getSelPrice1();
        };
    }

    /** Writes the item's own price on a tier. */
    public static void setItemPrice(ItemsModel item, int tierId, double price) {
        switch (orFirst(tierId)) {
            case 2 -> item.setSelPrice2(price);
            case 3 -> item.setSelPrice3(price);
            default -> item.setSelPrice1(price);
        }
    }

    /** A unit's own price on a tier - zero means it follows the item's price times its factor. */
    public static double unitPrice(ItemsUnitsModel row, int tierId) {
        if (row == null) {
            return 0;
        }
        return switch (orFirst(tierId)) {
            case 2 -> row.getSelPrice2();
            case 3 -> row.getSelPrice3();
            default -> row.getSelPrice();
        };
    }

    /** The column on {@code items} a tier's price lives in. Only an id this class owns enters SQL. */
    public static String itemColumn(int tierId) {
        return "sel_price" + orFirst(tierId);
    }

    /** The column on {@code items_units}: tier 1 is {@code sel_price}, with no digit, since V1. */
    public static String unitColumn(int tierId) {
        int tier = orFirst(tierId);
        return tier == FIRST ? "sel_price" : "sel_price" + tier;
    }
}
