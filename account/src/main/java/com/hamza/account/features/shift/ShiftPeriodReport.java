package com.hamza.account.features.shift;

import java.util.List;

/** Complete result exported and displayed under the exact query that produced it. */
public record ShiftPeriodReport(ShiftPeriodQuery query, List<ShiftPeriodRow> rows) {
    public ShiftPeriodReport {
        rows = List.copyOf(rows);
    }
}
