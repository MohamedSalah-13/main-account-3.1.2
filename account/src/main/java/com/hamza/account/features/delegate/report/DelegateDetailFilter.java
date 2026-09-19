package com.hamza.account.features.delegate.report;

import java.time.LocalDate;

/**
 * Whose detail, and over which days. The checks are here, so a filter that exists is one a
 * query can be run with; the refusals are the keys of their sentences.
 */
public record DelegateDetailFilter(int delegateId, LocalDate from, LocalDate to) {

    public DelegateDetailFilter {
        if (delegateId <= 0) {
            throw new IllegalArgumentException("delegate.detail.error.delegate");
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("delegate.detail.error.period");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("delegate.detail.error.period.order");
        }
    }

    /** delegate, from, to - twice: what every breakdown and the header discounts bind. */
    public Object[] breakdownParameters() {
        java.sql.Date first = java.sql.Date.valueOf(from);
        java.sql.Date last = java.sql.Date.valueOf(to);
        return new Object[]{delegateId, first, last, delegateId, first, last};
    }

    public Object[] collectionParameters() {
        return new Object[]{delegateId, java.sql.Date.valueOf(from), java.sql.Date.valueOf(to)};
    }
}
