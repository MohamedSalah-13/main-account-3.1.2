package com.hamza.account.features.treasury.statement;

import java.time.LocalDate;
import java.util.Objects;

public record TreasuryStatementFilter(LocalDate from, LocalDate to, Integer treasuryId,
                                      TreasuryMovementKind kind, Integer userId,
                                      int page, int pageSize) {
    public TreasuryStatementFilter {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) throw new IllegalArgumentException("from must not be after to");
        if (page < 0) throw new IllegalArgumentException("page must be non-negative");
        if (pageSize < 1 || pageSize > 10_000) throw new IllegalArgumentException("invalid page size");
    }

    public int offset() { return Math.multiplyExact(page, pageSize); }
    public int queryLimit() { return pageSize + 1; }

    public TreasuryStatementFilter firstPageWithSize(int size) {
        return new TreasuryStatementFilter(from, to, treasuryId, kind, userId, 0, size);
    }
}
