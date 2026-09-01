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

    /**
     * ما دخل الخزينة تحت بنود ليس لها سطر خاص في التقرير - تحصيلات العملاء،
     * مرتجع المشتريات، التحويلات الواردة. مجموع لا يُشتق منه شيء، وجوده حتى
     * لا يختفي جنيه من أمام الكاشير.
     */
    private double otherIn;

    /** وما خرج منها كذلك - مدفوعات الموردين، المشتريات النقدية، التحويلات الصادرة. */
    private double otherOut;

    /**
     * كل ما دخل الخزينة خلال الوردية وكل ما خرج منها، بكل البنود.
     * <p>
     * الرصيد المتوقع يُحسب منهما وحدهما. البنود المسمّاة أعلاه للعرض فقط: كانت
     * خمسة والحركات النقدية عشرة، فكان تحصيل من عميل يظهر زيادة في الدرج
     * ودفعة لمورّد تظهر عجزاً. انظر {@code ShiftCashSummary}.
     */
    private double totalIn;

    private double totalOut;

    /** عدد فواتير البيع خلال الوردية */
    private int invoicesCount;

    /**
     * الرصيد المتوقع في الصندوق في نهاية الوردية.
     * = الرصيد الافتتاحي + كل ما دخل - كل ما خرج
     */
    public double getExpectedBalance() {
        return openBalance + totalIn - totalOut;
    }

    /**
     * الفرق بين الرصيد الفعلي والمتوقع.
     * موجب = زيادة، سالب = عجز.
     */
    public double calculateDifference(double actualCloseBalance) {
        return actualCloseBalance - getExpectedBalance();
    }
}