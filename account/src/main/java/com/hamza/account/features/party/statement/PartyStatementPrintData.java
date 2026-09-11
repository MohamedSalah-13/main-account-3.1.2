package com.hamza.account.features.party.statement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The whole filtered statement, for printing and for export.
 * <p>
 * Read by a query of its own rather than taken from the page on screen — the same
 * separation {@code TreasuryStatementService.forPrint} makes. Printing the visible page
 * would hand the user a hundred rows of a statement that has two thousand, and exporting
 * the ticked rows while checking the table for emptiness is what {@code AccountController2}
 * does today: tick nothing and it writes an empty file and says it saved.
 *
 * @param rows      every row the filter matches, up to {@link PartyStatementService#PRINT_LIMIT}
 * @param summary   the period's figures
 * @param truncated whether the limit cut the extract short, which the caller must say out loud
 */
public record PartyStatementPrintData(List<PartyStatementRow> rows,
                                      PartyStatementSummary summary,
                                      boolean truncated) {
    public PartyStatementPrintData {
        rows = List.copyOf(rows);
    }

    /**
     * The same rows in statement order: oldest first.
     * <p>
     * {@link PartyStatementQuery#pageSql} orders newest first, because that is what a
     * paged list wants — page one should hold what happened today. A statement that is
     * printed or read in full wants the opposite, and it is the same order the running
     * balance was accumulated in, so each row's balance follows from the one above it
     * instead of contradicting it. One query, one place the order is turned round.
     */
    public List<PartyStatementRow> rowsOldestFirst() {
        List<PartyStatementRow> ordered = new ArrayList<>(rows);
        ordered.sort(Comparator.comparing(PartyStatementRow::date)
                .thenComparing(row -> row.enteredAt() == null
                        ? row.date().atStartOfDay() : row.enteredAt())
                .thenComparingInt(row -> row.kind().code())
                .thenComparingLong(PartyStatementRow::movementId));
        return List.copyOf(ordered);
    }
}
