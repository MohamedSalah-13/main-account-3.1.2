package com.hamza.account.features.treasury.statement;

import java.util.List;

public record TreasuryStatementPage(List<TreasuryStatementRow> rows,
                                    TreasuryStatementSummary summary,
                                    int page, boolean hasPrevious, boolean hasNext) {
    public TreasuryStatementPage {
        rows = List.copyOf(rows);
    }
}
