package com.hamza.account.features.shift;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/** A bounded period and optional cashier/till filters for the supervisor report. */
public record ShiftPeriodQuery(LocalDate from, LocalDate to, Integer userId, Integer treasuryId) {

    public ShiftPeriodQuery {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (to.isBefore(from)) throw new IllegalArgumentException("to is before from");
        userId = positiveOrNull(userId);
        treasuryId = positiveOrNull(treasuryId);
    }

    public LocalDateTime fromInclusive() {
        return from.atStartOfDay();
    }

    public LocalDateTime toExclusive() {
        return to.plusDays(1).atStartOfDay();
    }

    private static Integer positiveOrNull(Integer value) {
        return value == null || value <= 0 ? null : value;
    }
}
