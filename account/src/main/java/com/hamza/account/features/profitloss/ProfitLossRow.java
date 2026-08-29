package com.hamza.account.features.profitloss;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A daily, auditable profit-and-loss result. */
public record ProfitLossRow(LocalDate date, BigDecimal netSales, BigDecimal costOfSales,
                            BigDecimal grossProfit, BigDecimal expenses, BigDecimal netProfit) { }