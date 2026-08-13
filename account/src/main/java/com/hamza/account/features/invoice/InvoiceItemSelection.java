package com.hamza.account.features.invoice;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;

import java.util.List;
import java.util.Objects;

/** Immutable result of resolving an item for the invoice entry form. */
public record InvoiceItemSelection(
        ItemsModel item,
        List<UnitsModel> units,
        UnitsModel selectedUnit,
        String barcode,
        double price,
        double quantity,
        double total,
        double balance,
        boolean scaleBarcode) {

    public InvoiceItemSelection {
        Objects.requireNonNull(item, "item");
        units = List.copyOf(units);
        Objects.requireNonNull(selectedUnit, "selectedUnit");
    }
}
