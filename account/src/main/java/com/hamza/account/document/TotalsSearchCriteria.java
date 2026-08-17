package com.hamza.account.document;

import com.hamza.account.type.InvoiceType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Every optional way a totals list can be narrowed, plus the date range every search
 * carries. A blank string or a null field means "not filtering by this" -
 * {@link DocumentTableSpec#searchSql} only appends a condition for what is present.
 */
public record TotalsSearchCriteria(
        LocalDate dateFrom,
        LocalDate dateTo,
        Integer invoiceNumber,
        String partyName,
        String delegateName,
        InvoiceType invoiceType,
        String enteredByUsername,
        BigDecimal minTotal,
        BigDecimal maxTotal,
        String freeText) {

    public static TotalsSearchCriteria dateRangeOnly(LocalDate dateFrom, LocalDate dateTo) {
        return new TotalsSearchCriteria(dateFrom, dateTo, null, null, null, null, null, null, null, null);
    }
}
