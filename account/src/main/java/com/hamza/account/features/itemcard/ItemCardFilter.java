package com.hamza.account.features.itemcard;

import com.hamza.account.type.ProcessType;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The period and optional document kind requested by the item-card screen.
 *
 * <p>The record keeps the date rules outside JavaFX, so the controller only translates a
 * refused filter key into the active language. The quick periods live here for the same
 * reason: the buttons are UI, but what "this month" means is ordinary date arithmetic.</p>
 */
public record ItemCardFilter(LocalDate from, LocalDate to, ProcessType processType) {

    public ItemCardFilter {
        if (from == null || to == null) {
            throw new IllegalArgumentException("item.card.date.required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("item.card.date.range.invalid");
        }
    }

    public static DateRange today(LocalDate today) {
        LocalDate date = Objects.requireNonNull(today, "today");
        return new DateRange(date, date);
    }

    public static DateRange monthToDate(LocalDate today) {
        LocalDate date = Objects.requireNonNull(today, "today");
        return new DateRange(date.withDayOfMonth(1), date);
    }

    public static DateRange wholeHistory(LocalDate firstMovement, LocalDate today) {
        LocalDate end = Objects.requireNonNull(today, "today");
        LocalDate start = firstMovement == null ? end : firstMovement;
        return new DateRange(start, end);
    }

    public record DateRange(LocalDate from, LocalDate to) {
        public DateRange {
            if (from == null || to == null || from.isAfter(to)) {
                throw new IllegalArgumentException("item.card.date.range.invalid");
            }
        }
    }
}
