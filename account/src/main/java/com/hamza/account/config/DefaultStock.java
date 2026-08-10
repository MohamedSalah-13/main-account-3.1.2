package com.hamza.account.config;

/**
 * The one stock every document belongs to.
 * <p>
 * Multi-warehouse support was removed from the application: the transfer screens,
 * the warehouse management screen and the per-document warehouse pickers are gone,
 * and nothing lets a user create a second stock any more.
 * <p>
 * The <b>schema is deliberately unchanged</b>. {@code stocks}, {@code items_stock}
 * and the {@code stock_id} columns on the four invoice tables all still exist, and
 * every write still carries a warehouse id - it is just always this one, the
 * {@code 'الرئيسي'} row seeded by {@code V1__baseline.sql}. Keeping the columns
 * means no migration runs against a client database, and re-introducing warehouses
 * later is a matter of restoring screens rather than restoring data.
 * <p>
 * Two things follow from that, and both are load-bearing:
 * <ul>
 *   <li>Every DAO that writes a {@code stock_id} must use this constant. Writing a
 *       different id would produce rows no screen can reach.</li>
 *   <li>The views that aggregate balances ({@code quantity_items_table} and the
 *       {@code *_names_table} family) still group by {@code stock_id}. They keep
 *       working untouched precisely because every row carries the same id.</li>
 * </ul>
 */
public final class DefaultStock {

    /** Primary key of the seeded {@code 'الرئيسي'} row in {@code stocks}. */
    public static final int ID = 1;

    private DefaultStock() {
    }
}
