package com.hamza.account.features.invoice;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result of resolving an item for the invoice entry form.
 *
 * @param price  the selected unit's price on the screen's tier, in the screen's currency
 * @param listed the list price behind that price, and whether it is tier 1's standing in for a tier
 *               with none (docs/pricing-and-offers-plan.md ق-س٣) - null on a purchase, where no tier
 *               stands behind a price
 */
public record InvoiceItemSelection(
        ItemsModel item,
        List<UnitsModel> units,
        UnitsModel selectedUnit,
        String barcode,
        double price,
        double quantity,
        double total,
        double balance,
        boolean scaleBarcode,
        InvoiceLineDraft.Listed listed) {

    public InvoiceItemSelection {
        Objects.requireNonNull(item, "item");
        units = List.copyOf(units);
        Objects.requireNonNull(selectedUnit, "selectedUnit");
    }

    public InvoiceItemSelection(ItemsModel item, List<UnitsModel> units, UnitsModel selectedUnit,
                                String barcode, double price, double quantity, double total,
                                double balance, boolean scaleBarcode) {
        this(item, units, selectedUnit, barcode, price, quantity, total, balance, scaleBarcode, null);
    }

    /** Whether the price is tier 1's standing in for the screen's tier. */
    public boolean fromFirstTier() {
        return listed != null && listed.fromFirstTier();
    }

    /** The line this selection makes, at its own price and quantity, with its list price behind it. */
    public InvoiceLineDraft draft() {
        return new InvoiceLineDraft(item, selectedUnit, quantity, price, 0, null, listed);
    }
}
