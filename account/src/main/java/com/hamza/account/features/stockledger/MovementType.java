package com.hamza.account.features.stockledger;

/**
 * Mirrors {@code stock_movements_type_chk} in {@code V1__baseline.sql} exactly - the
 * database is the source of truth for what a movement may say it is, and this enum's
 * job is only to keep Java from writing a string the check constraint would reject.
 * <p>
 * {@link #TRANSFER_IN}, {@link #TRANSFER_OUT} and {@link #OPENING} have no producer, and
 * that is now a gap rather than a description. This javadoc used to explain it by saying
 * transfers have no live write path because the multi-warehouse screens were removed in
 * {@code 0853cf4} - {@code fbadd53} brought them back, {@code StockTransferService} posts
 * transfers every day, and it writes nothing here. So of the six things that move stock,
 * five write this ledger and the transfer does not, which means
 * {@code StockLedgerReconciliationReport} counts a transfer on the view's side only and
 * should report a mismatch for every item one has ever moved. Nothing reads the ledger for
 * a figure, so no balance is wrong today; the reconciliation is.
 * <p>
 * The opening balance has no producer either, and it has moved: since {@code V18} it lives
 * on {@code items_stock.first_balance}, per warehouse, not on {@code items.first_balance}.
 * <p>
 * Both gaps belong to phase F of {@code docs/warehouse-plan.md}, where the ledger becomes
 * the source of a balance. Until then they are declared so the {@code CHECK} constraint and
 * this enum stay in lockstep.
 */
public enum MovementType {
    OPENING,
    PURCHASE,
    PURCHASE_RETURN,
    SALE,
    SALE_RETURN,
    TRANSFER_IN,
    TRANSFER_OUT,
    INVENTORY_ADJUST_IN,
    INVENTORY_ADJUST_OUT
}
