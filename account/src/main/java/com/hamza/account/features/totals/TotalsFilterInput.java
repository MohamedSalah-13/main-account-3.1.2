package com.hamza.account.features.totals;

import com.hamza.account.document.TotalsSearchCriteria;
import com.hamza.account.type.InvoiceType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The values entered in the totals screen's search controls.
 *
 * <p>The controller only reads controls and localizes a rejected field. Parsing,
 * normalization and range validation live here so they can be checked without a
 * JavaFX toolkit.</p>
 */
public record TotalsFilterInput(
        LocalDate dateFrom,
        LocalDate dateTo,
        String invoiceNumber,
        String partyName,
        String delegateName,
        InvoiceType invoiceType,
        String enteredByUsername,
        String minTotal,
        String maxTotal,
        String freeText) {

    /**
     * Neither date is required. An operator looking for a customer's first invoice does
     * not know when they started, and stepping a date picker backwards a month at a time
     * until something appears is not a search - so an empty date is "no bound on that
     * side" rather than a refusal. Only an inverted range is still refused, because it
     * can match nothing and is always a mistake.
     */
    public TotalsSearchCriteria toCriteria() throws InvalidFilterException {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new InvalidFilterException(Problem.DATE_RANGE);
        }

        Integer parsedInvoice = parseInvoiceNumber(invoiceNumber);
        BigDecimal parsedMinimum = parseDecimal(minTotal, Problem.MIN_TOTAL);
        BigDecimal parsedMaximum = parseDecimal(maxTotal, Problem.MAX_TOTAL);
        if (parsedMinimum != null && parsedMaximum != null
                && parsedMinimum.compareTo(parsedMaximum) > 0) {
            throw new InvalidFilterException(Problem.TOTAL_RANGE);
        }

        return new TotalsSearchCriteria(
                dateFrom,
                dateTo,
                parsedInvoice,
                normalize(partyName),
                normalize(delegateName),
                invoiceType,
                normalize(enteredByUsername),
                parsedMinimum,
                parsedMaximum,
                normalize(freeText));
    }

    /** Conditions hidden inside the collapsible panel; the always-visible text is excluded. */
    public static int hiddenConditionCount(TotalsSearchCriteria criteria) {
        int count = 0;
        if (criteria.dateFrom() != null || criteria.dateTo() != null) count++;
        if (criteria.invoiceNumber() != null) count++;
        if (criteria.partyName() != null) count++;
        if (criteria.delegateName() != null) count++;
        if (criteria.invoiceType() != null) count++;
        if (criteria.enteredByUsername() != null) count++;
        if (criteria.minTotal() != null || criteria.maxTotal() != null) count++;
        return count;
    }

    private static Integer parseInvoiceNumber(String value) throws InvalidFilterException {
        String normalized = normalize(value);
        if (normalized == null) return null;
        try {
            int number = Integer.parseInt(normalized);
            if (number <= 0) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException e) {
            throw new InvalidFilterException(Problem.INVOICE_NUMBER);
        }
    }

    private static BigDecimal parseDecimal(String value, Problem problem) throws InvalidFilterException {
        String normalized = normalize(value);
        if (normalized == null) return null;
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            throw new InvalidFilterException(problem);
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    public enum Problem {
        DATE_RANGE,
        INVOICE_NUMBER,
        MIN_TOTAL,
        MAX_TOTAL,
        TOTAL_RANGE
    }

    public static final class InvalidFilterException extends Exception {
        private final Problem problem;

        public InvalidFilterException(Problem problem) {
            super(problem.name());
            this.problem = problem;
        }

        public Problem problem() {
            return problem;
        }
    }
}
