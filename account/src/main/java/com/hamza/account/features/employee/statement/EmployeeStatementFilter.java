package com.hamza.account.features.employee.statement;

import com.hamza.account.features.employee.EmployeeMovementSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What one employee's statement is narrowed by.
 * <p>
 * <b>A filter narrows the rows; the two balances answer the dates alone.</b> The opening and
 * closing figures ignore everything else here, exactly as
 * {@code TreasuryStatements.SELECT_STATEMENT_SUMMARY} and {@code PartyStatementFilter} do. A
 * "balance before the period" measured over deductions only is not anybody's balance - and this
 * is the number an employee is asked to agree with before they sign for their pay. A new filter
 * goes in {@link EmployeeStatementQuery#rowFilterSql}, never near the opening query.
 *
 * @param employeeId whose account. Every statement here is scoped to one employee; what every
 *                   employee is owed is a different question with its own view
 *                   ({@code employee_balance})
 * @param kinds      the movement kinds to keep, or empty for all. Codes, never labels
 * @param source     only the cash half, only the ledger half, or {@code null} for both
 * @param userId     only what this user entered, or {@code null}
 * @param minAmount  smallest movement to show, by magnitude, or {@code null}
 * @param maxAmount  largest, or {@code null}
 * @param text       matched against the note
 */
public record EmployeeStatementFilter(int employeeId,
                                      LocalDate from,
                                      LocalDate to,
                                      Set<String> kinds,
                                      EmployeeMovementSource source,
                                      Integer userId,
                                      BigDecimal minAmount,
                                      BigDecimal maxAmount,
                                      String text,
                                      int page,
                                      int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 10_000;

    public EmployeeStatementFilter {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        if (employeeId <= 0) {
            throw new IllegalArgumentException("a statement is scoped to one employee");
        }
        kinds = kinds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(kinds));
        text = text == null ? "" : text.strip();
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new IllegalArgumentException("minAmount must not be above maxAmount");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be non-negative");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    /** The whole of one employee's history, on its first page. */
    public static EmployeeStatementFilter all(int employeeId, LocalDate from, LocalDate to) {
        return new EmployeeStatementFilter(employeeId, from, to, Set.of(), null, null, null, null,
                "", 0, DEFAULT_PAGE_SIZE);
    }

    public boolean hasText() {
        return !text.isEmpty();
    }

    /**
     * The kinds as one comma-joined value for {@code FIND_IN_SET}, or {@code null} for all.
     * <p>
     * Bound as a single parameter on purpose: an {@code IN (?, ?, ?)} whose length follows the
     * user's selection is a different statement per selection, and nothing could pin any of them.
     * The codes are the enums' own names, so nothing a user typed reaches it.
     */
    public String kindList() {
        return kinds.isEmpty() ? null : kinds.stream().sorted().collect(Collectors.joining(","));
    }

    public String sourceCode() {
        return source == null ? null : source.name();
    }

    /** One row more than the page holds, which is what answers "is there another page". */
    public int queryLimit() {
        return pageSize + 1;
    }

    public int offset() {
        return page * pageSize;
    }

    public EmployeeStatementFilter onPage(int index) {
        return new EmployeeStatementFilter(employeeId, from, to, kinds, source, userId, minAmount,
                maxAmount, text, index, pageSize);
    }

    public EmployeeStatementFilter firstPageWithSize(int size) {
        return new EmployeeStatementFilter(employeeId, from, to, kinds, source, userId, minAmount,
                maxAmount, text, 0, size);
    }

    public EmployeeStatementFilter withPeriod(LocalDate newFrom, LocalDate newTo) {
        return new EmployeeStatementFilter(employeeId, newFrom, newTo, kinds, source, userId,
                minAmount, maxAmount, text, 0, pageSize);
    }

    public EmployeeStatementFilter withKinds(Set<String> value) {
        return new EmployeeStatementFilter(employeeId, from, to, value, source, userId, minAmount,
                maxAmount, text, 0, pageSize);
    }

    public EmployeeStatementFilter withSource(EmployeeMovementSource value) {
        return new EmployeeStatementFilter(employeeId, from, to, kinds, value, userId, minAmount,
                maxAmount, text, 0, pageSize);
    }

    public EmployeeStatementFilter withUser(Integer value) {
        return new EmployeeStatementFilter(employeeId, from, to, kinds, source, value, minAmount,
                maxAmount, text, 0, pageSize);
    }

    public EmployeeStatementFilter withAmounts(BigDecimal min, BigDecimal max) {
        return new EmployeeStatementFilter(employeeId, from, to, kinds, source, userId, min, max,
                text, 0, pageSize);
    }

    public EmployeeStatementFilter withText(String value) {
        return new EmployeeStatementFilter(employeeId, from, to, kinds, source, userId, minAmount,
                maxAmount, value, 0, pageSize);
    }

    /** Whether anything beyond the period narrows the statement - what "clear" is enabled by. */
    public boolean isNarrowed() {
        return !kinds.isEmpty() || source != null || userId != null || minAmount != null
                || maxAmount != null || hasText();
    }
}
