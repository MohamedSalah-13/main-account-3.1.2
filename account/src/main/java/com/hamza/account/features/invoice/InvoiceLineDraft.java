package com.hamza.account.features.invoice;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;

import java.time.LocalDate;

/**
 * Immutable input captured from the invoice line editor.
 *
 * @param listed what the price tier's list says the unit sells for, in the screen's currency, or
 *               {@code null} where no list stands behind the line - a purchase, a return picked from
 *               its invoice, a line reopened from before V84 (docs/pricing-and-offers-plan.md ق-س٤)
 */
public record InvoiceLineDraft(
        ItemsModel item,
        UnitsModel unit,
        double quantity,
        double price,
        double discount,
        LocalDate expirationDate,
        Listed listed) {

    /** The list price behind a line, and whether it is tier 1's standing in for a tier that had none. */
    public record Listed(double price, boolean fromFirstTier) {
    }

    public InvoiceLineDraft(ItemsModel item, UnitsModel unit, double quantity, double price,
                            double discount, LocalDate expirationDate) {
        this(item, unit, quantity, price, discount, expirationDate, null);
    }

    public InvoiceLineDraft withExpirationDate(LocalDate date) {
        return new InvoiceLineDraft(item, unit, quantity, price, discount, date, listed);
    }

    public InvoiceLineDraft withListed(Listed listed) {
        return new InvoiceLineDraft(item, unit, quantity, price, discount, expirationDate, listed);
    }
}
