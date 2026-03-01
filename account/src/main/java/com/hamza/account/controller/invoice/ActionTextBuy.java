package com.hamza.account.controller.invoice;

import com.hamza.controlsfx.language.Setting_Language;

import java.time.LocalDate;

public interface ActionTextBuy {

    /**
     * Adds a row to a table with the specified details. Throws an exception if the barcode is empty.
     *
     * @param barcode  The barcode of the item.
     * @param quantity The quantity of the item.
     * @param price    The price of the item.
     * @param discount The discount applied to the item.
     * @param total    The total cost of the item.
     * @return An integer indicating the success of the operation.
     * @throws Exception If the barcode is empty.
     */
    default int addRowToTable(String barcode, double quantity, double price, double discount, double total, LocalDate expireDate) throws Exception {
        if (barcode.isEmpty()) throw new Exception(Setting_Language.NO_ITEM);
        return 0;
    }
}
