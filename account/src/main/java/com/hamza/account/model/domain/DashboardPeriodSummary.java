package com.hamza.account.model.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * The same shape {@code daily_dashboard_report} exposes for "today", but for an
 * arbitrary [from, to] date range - so the dashboard's period selector can ask for
 * "this week"/"this month"/a custom range without a fixed view per period.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardPeriodSummary {
    private Long salesCount;
    private BigDecimal salesTotal;
    private Long purchasesCount;
    private BigDecimal purchasesTotal;
    private BigDecimal totalReceipts;
    private BigDecimal totalPaymentsAndExpenses;
    private BigDecimal totalDiscounts;
}
