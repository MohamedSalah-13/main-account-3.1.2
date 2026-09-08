package com.hamza.account.features.treasury.statement;

import java.util.List;

public record TreasuryStatementPrintData(List<TreasuryStatementRow> rows,
                                         TreasuryStatementSummary summary,
                                         boolean truncated) {
    public TreasuryStatementPrintData {
        rows = List.copyOf(rows);
    }
}
