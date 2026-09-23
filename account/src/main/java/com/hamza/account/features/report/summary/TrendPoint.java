package com.hamza.account.features.report.summary;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One day of the summary's trend: the day's net sales, a quiet day as zero. */
public record TrendPoint(LocalDate day, BigDecimal net) {
}
