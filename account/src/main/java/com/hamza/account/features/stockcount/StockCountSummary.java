package com.hamza.account.features.stockcount;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One count sheet as the history lists it: its header, how many lines it carries and how many of
 * them found a difference, and who entered it.
 *
 * @param postedAt        when it was posted, or {@code null} for a draft
 * @param enteredBy       the user who entered it, or {@code null} for a row whose user has gone
 * @param differenceCount lines whose count differs from the book - the ones that move a balance
 */
public record StockCountSummary(int id, LocalDate countDate, int stockId, String stockName, StockCountStatus status,
                                String notes, LocalDateTime postedAt, String enteredBy, int lineCount,
                                int differenceCount) {
}
