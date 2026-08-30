package com.hamza.account.config;

/**
 * Which warehouse, when nothing else says.
 * <p>
 * <b>Not "the only one".</b> Multi-warehouse support was removed once and came back in
 * {@code fbadd53}: the stocks screen, warehouse transfers and per-warehouse balances
 * are all live again, and an operation that reads or writes a specific warehouse's
 * balance takes a {@code stockId}. This constant answers the narrower question - a
 * combo's initial selection, a compatibility overload kept for an old caller, the one
 * opening-balance field the item screen has never had a picker for - and it is the
 * seeded {@code 'الرئيسي'} row from {@code V1__baseline.sql}, the DEFAULT behind every
 * {@code stock_id} column.
 * <p>
 * Reaching for it instead of threading a real {@code stockId} through is how a
 * warehouse gets silently ignored, and that is not a judgement a regex can make - so
 * {@code DefaultStockUsageArchitectureTest} carries the list of files allowed to
 * reference it at all. A new file that does fails the build; adding it to the list is a
 * decision made in the same review that adds the reference.
 *
 * @see com.hamza.account.treasury.DefaultTreasury the same idea on the money side
 */
public final class DefaultStock {

    /** Primary key of the seeded {@code 'الرئيسي'} row in {@code stocks}. */
    public static final int ID = 1;

    private DefaultStock() {
    }
}
