package com.hamza.account.features.stockcount;

import java.util.List;

/**
 * A warehouse's items on paper, to be counted by hand - ورقة العدّ.
 * <p>
 * A shop counting a warehouse walks the shelves with a pen long before anybody scans: the count
 * screen took a scanned code or a typed name, and there was no paper to take into the store room.
 * This is that paper: every item in use that has a row in the warehouse, by group and then by name
 * so it follows the shelves, with an empty column for what was found.
 * <p>
 * <b>No book quantity is printed.</b> A counter who can read what the system expects writes that
 * down; a blind count is the only one whose differences mean anything. The book is what the screen
 * compares against when the figures are typed in.
 *
 * @param stockName the counted warehouse
 */
public record StockCountBlankSheet(String stockName, List<Row> rows) {

    public StockCountBlankSheet {
        rows = List.copyOf(rows);
    }

    /** One item to count, in its base unit - the unit the book is kept in. */
    public record Row(String code, String name, String group, String unit) {
    }

    /** {@code (stock)}. Items in use with a row in the warehouse, in shelf order. */
    public static final String SQL = """
            SELECT i.barcode, i.nameItem, sg.name AS group_name, u.unit_name
            FROM items_stock ist
                     JOIN items i ON i.id = ist.item_id
                     LEFT JOIN sub_group sg ON sg.id = i.sub_num
                     LEFT JOIN units u ON u.unit_id = i.unit_id
            WHERE ist.stock_id = ? AND i.item_active = 1
            ORDER BY sg.name, i.nameItem, i.id""";
}
