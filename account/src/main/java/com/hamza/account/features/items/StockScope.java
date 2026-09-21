package com.hamza.account.features.items;

/**
 * Which warehouses a picker offers - an argument to {@code StockService.stocksForPicker}, and
 * never a default.
 * <p>
 * The two kinds of screen want opposite answers, which is the {@code PartySearchScope} and
 * {@code EmployeeScope} lesson a third time. A screen that <b>writes a new movement</b> - an
 * invoice, a transfer, a count, the price check - offers only the warehouses still in use: a
 * switched-off one takes nothing new. A screen that <b>reads what happened</b> - the item card, the
 * inventory sheet, the transfer and count histories - offers every warehouse, because a warehouse
 * closed in June still holds everything that moved through it before June, and hiding it there
 * would make its history unreachable. A default would silently give one kind the other's answer.
 */
public enum StockScope {

    /** For a document being written: only the warehouses in use. */
    ACTIVE_ONLY,

    /** For history, balances and reports: every warehouse there has been. */
    EVERYONE
}
