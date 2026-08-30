package com.hamza.account.treasury;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What an e-wallet keeps out of a collection, and what the customer still owes.
 * <p>
 * A customer who settles 1000 on فودافون كاش has paid 1000 - their account closes by
 * the whole of it - while the wallet credits, say, 990. The 10 is <b>the shop's
 * expense</b>, not a shortfall on the customer. Netting it off the collection instead
 * is the mistake this class exists to prevent: it leaves the customer owing 10 for
 * ever, over and over, on every wallet payment they ever make.
 * <p>
 * So the arithmetic here has one job - say what the fee is - and the posting is two
 * rows that already existed: the payment for the full amount, and an expense for the
 * fee on the same treasury. The treasury balance is derived from both, so it comes out
 * at +990 with nothing new to compute.
 * <p>
 * It lives in {@code account.treasury} rather than in {@code features/} for the reason
 * {@link MovementLabel} does: {@link #EXPENSE_NAME} is not user-facing text but a value
 * the database already holds - the heading {@code V21} seeded - and it has to keep
 * matching that row exactly. Translating it would stop the lookup finding anything.
 * {@code LocalizationArchitectureTest} would otherwise read it as a service building
 * Arabic text, which is a different mistake and a real one.
 * <p>
 * Rounding is HALF_UP to two decimals, the scale every money column in this schema
 * uses. A fee is a percentage of an amount and will not land on a whole piastre on its
 * own; rounding it once, here, keeps the stored expense and the figure the screen
 * showed the same number.
 */
public final class WalletFee {

    /** The expense heading seeded by {@code V21__treasury_capital.sql}. */
    public static final String EXPENSE_NAME = "عمولات تحويل";

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    private WalletFee() {
    }

    /**
     * The fee on {@code amount} at {@code percent}, or zero when either is missing or
     * not positive - a cash drawer charges nothing, and neither does a wallet whose
     * percentage nobody has filled in.
     */
    public static BigDecimal on(BigDecimal amount, BigDecimal percent) {
        if (amount == null || percent == null
                || amount.signum() <= 0 || percent.signum() <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        return amount.multiply(percent)
                .divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
    }

    /** What actually reaches the treasury: the collection less the fee. */
    public static BigDecimal net(BigDecimal amount, BigDecimal fee) {
        BigDecimal paid = amount == null ? BigDecimal.ZERO : amount;
        BigDecimal charged = fee == null ? BigDecimal.ZERO : fee;
        return paid.subtract(charged).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * A fee has to be smaller than what it is charged on. Anything else is a typo -
     * and it would post an expense that empties the treasury the collection just
     * filled.
     */
    public static boolean isPlausible(BigDecimal amount, BigDecimal fee) {
        if (fee == null || fee.signum() < 0) {
            return false;
        }
        return amount != null && fee.compareTo(amount) < 0;
    }
}
