package com.hamza.account.treasury;

/**
 * The treasury every cash column falls back to.
 * <p>
 * {@code treasury_id} is {@code DEFAULT 1} on all eight tables that carry it, and
 * row 1 is the {@code 'الخزينة الرئيسية'} seeded by {@code V1__baseline.sql}. So a
 * row written without a treasury still resolves to something a screen can reach -
 * which is exactly why the id has to be named in one place rather than typed as a
 * literal {@code 1} wherever a default is needed.
 * <p>
 * Unlike {@link com.hamza.account.config.DefaultStock}, this is <b>not</b> the only
 * id in play: several treasuries are a supported, everyday case (cash drawer,
 * e-wallet, bank). This constant answers "which one when nobody chose", not "the
 * only one there is". {@code DeleteRegistry.TREASURIES} protects it from deletion
 * for the same reason.
 */
public final class DefaultTreasury {

    /** Primary key of the seeded {@code 'الخزينة الرئيسية'} row in {@code treasury}. */
    public static final int ID = 1;

    private DefaultTreasury() {
    }
}
