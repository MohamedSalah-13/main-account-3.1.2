package com.hamza.account.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ملخص مالي للوردية يحوي إجماليات الحركات المالية خلال فترة فتحها.
 * يُحسب عند الطلب (X-Report) أو عند غلق الوردية (Z-Report).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftSummary {

    /** الرصيد الافتتاحي للوردية */
    private double openBalance;

    /** إجمالي المبيعات النقدية (paid_up من total_sales) */
    private double totalSales;

    /** إجمالي مرتجعات المبيعات النقدية */
    private double totalSalesReturns;

    /** إجمالي المصروفات من الخزينة */
    private double totalExpenses;

    /** إجمالي الإيداعات على الخزينة */
    private double totalDeposits;

    /** إجمالي السحوبات من الخزينة */
    private double totalWithdrawals;

    /** عدد فواتير البيع خلال الوردية */
    private int invoicesCount;

    /**
     * الرصيد المتوقع في الصندوق في نهاية الوردية.
     * = الرصيد الافتتاحي + المبيعات - مرتجعات المبيعات - المصروفات + الإيداعات - السحوبات
     */
    public double getExpectedBalance() {
        return openBalance
                + totalSales
                - totalSalesReturns
                - totalExpenses
                + totalDeposits
                - totalWithdrawals;
    }

    /**
     * الفرق بين الرصيد الفعلي والمتوقع.
     * موجب = زيادة، سالب = عجز.
     */
    public double calculateDifference(double actualCloseBalance) {
        return actualCloseBalance - getExpectedBalance();
    }
}