package com.hamza.account.features.totals;

import com.hamza.account.finance.MoneyMath;

import java.math.BigDecimal;
import java.util.function.Function;

/** A one-pass financial summary of the rows currently displayed by a totals screen. */
public record TotalsSummary(
        int count,
        BigDecimal total,
        BigDecimal discount,
        BigDecimal afterDiscount,
        BigDecimal paid,
        BigDecimal remaining,
        BigDecimal profit) {

    public static <T> TotalsSummary calculate(Iterable<T> rows, Function<T, Amounts> amounts) {
        int count = 0;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal afterDiscount = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal profit = BigDecimal.ZERO;

        for (T row : rows) {
            Amounts value = amounts.apply(row);
            count++;
            total = total.add(MoneyMath.decimal(value.total()));
            discount = discount.add(MoneyMath.decimal(value.discount()));
            afterDiscount = afterDiscount.add(MoneyMath.decimal(value.afterDiscount()));
            paid = paid.add(MoneyMath.decimal(value.paid()));
            profit = profit.add(MoneyMath.decimal(value.profit()));
        }

        BigDecimal roundedTotal = MoneyMath.money(total);
        BigDecimal roundedDiscount = MoneyMath.money(discount);
        BigDecimal roundedAfterDiscount = MoneyMath.money(afterDiscount);
        BigDecimal roundedPaid = MoneyMath.money(paid);
        return new TotalsSummary(
                count,
                roundedTotal,
                roundedDiscount,
                roundedAfterDiscount,
                roundedPaid,
                MoneyMath.subtract(roundedAfterDiscount, roundedPaid),
                MoneyMath.money(profit));
    }

    public record Amounts(double total, double discount, double afterDiscount, double paid, double profit) {
    }
}
