package com.hamza.account.model.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyDashboardReport {

    // --- المبيعات ---
    private Long salesCountToday;
    private BigDecimal salesTotalToday;
    private BigDecimal salesTotalYesterday;
    private BigDecimal salesTotalWeek;
    private BigDecimal salesTotalMonth;

    // --- المشتريات ---
    private Long purchasesCountToday;
    private BigDecimal purchasesTotalToday;

    // --- مرتجعات المبيعات ---
    private Long salesReturnsCountToday;
    private BigDecimal salesReturnsTotalToday;

    // --- مرتجعات المشتريات ---
    private Long purchasesReturnsCountToday;
    private BigDecimal purchasesReturnsTotalToday;

    // --- المقبوضات والمصروفات والخصومات ---
    private BigDecimal totalReceiptsToday;
    private BigDecimal totalPaymentsAndExpensesToday;
    private BigDecimal totalDiscountsToday;
}
