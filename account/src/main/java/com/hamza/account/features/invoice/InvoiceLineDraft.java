package com.hamza.account.features.invoice;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;

import java.time.LocalDate;

/** Immutable input captured from the invoice line editor. */
public record InvoiceLineDraft(
        ItemsModel item,
        UnitsModel unit,
        double quantity,
        double price,
        double discount,
        LocalDate expirationDate) {

    public InvoiceLineDraft withExpirationDate(LocalDate date) {
        return new InvoiceLineDraft(item, unit, quantity, price, discount, date);
    }
}
