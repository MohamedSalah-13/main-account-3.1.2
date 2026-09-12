package com.hamza.account.features.employee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * What an employees list is narrowed by.
 * <p>
 * Every one of these used to be impossible. The screen read {@code SELECT * FROM employees}
 * entire, or searched it with a hand-rolled three-phase {@code LIKE} capped at fifty rows and
 * merged in a {@code LinkedHashMap} - so there was no second page of a search, and no filter
 * at all beyond the text box.
 * <p>
 * One record reaches the page, the summary, the print and the export, so an exported file
 * cannot describe a different set of employees from the table - the rule
 * {@code ItemsDao.catalogQuery} and {@code PartyStatementFilter} both carry.
 *
 * @param text           matched against the name, the telephone, the national id and the code
 * @param jobId          one job, or {@code null}
 * @param state          still working, left, or everyone
 * @param salaryKind     monthly / daily / hourly / commission, or {@code null}
 * @param employmentType one contract type, or {@code null}
 * @param hiredFrom      hired on or after this day, or {@code null}
 * @param hiredTo        hired on or before this day, or {@code null}
 * @param minRate        smallest current rate to show, or {@code null}. <b>Refused outright
 *                       for a reader without {@code employees.show.salary}</b> - see
 *                       {@link EmployeeService}: a filter you can move is a way of reading the
 *                       figure it filters on, one comparison at a time
 * @param maxRate        largest, on the same terms
 * @param delegatesOnly  only employees whose job is a delegate job
 * @param page           zero-based page index
 * @param pageSize       rows per page
 */
public record EmployeeFilter(String text,
                             Integer jobId,
                             EmployeeState state,
                             SalaryKind salaryKind,
                             EmploymentType employmentType,
                             LocalDate hiredFrom,
                             LocalDate hiredTo,
                             BigDecimal minRate,
                             BigDecimal maxRate,
                             boolean delegatesOnly,
                             int page,
                             int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 10_000;

    public EmployeeFilter {
        state = state == null ? EmployeeState.ALL : state;
        text = text == null ? "" : text.strip();
        if (hiredFrom != null && hiredTo != null && hiredFrom.isAfter(hiredTo)) {
            throw new IllegalArgumentException("hiredFrom must not be after hiredTo");
        }
        if (minRate != null && maxRate != null && minRate.compareTo(maxRate) > 0) {
            throw new IllegalArgumentException("minRate must not be above maxRate");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be non-negative");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    /** The whole list, unfiltered, on its first page. */
    public static EmployeeFilter all() {
        return new EmployeeFilter("", null, EmployeeState.ALL, null, null, null, null, null, null,
                false, 0, DEFAULT_PAGE_SIZE);
    }

    public boolean hasText() {
        return !text.isEmpty();
    }

    /** Whether the text could be a code or a telephone, which is what an exact match is tried on. */
    public boolean textIsNumeric() {
        return hasText() && text.chars().allMatch(Character::isDigit);
    }

    /** The text as a code, or -1 when it is not one. Never throws: a long run of digits is a phone. */
    public int numericText() {
        try {
            return textIsNumeric() ? Integer.parseInt(text) : -1;
        } catch (NumberFormatException overflow) {
            return -1;
        }
    }

    public boolean filtersOnRate() {
        return minRate != null || maxRate != null;
    }

    /**
     * One row more than the page holds, which is what answers "is there another page" without
     * a second count that could fall out of step with the conditions of the page itself.
     */
    public int queryLimit() {
        return pageSize + 1;
    }

    public int offset() {
        return page * pageSize;
    }

    public EmployeeFilter firstPageWithSize(int size) {
        return new EmployeeFilter(text, jobId, state, salaryKind, employmentType, hiredFrom, hiredTo,
                minRate, maxRate, delegatesOnly, 0, size);
    }

    public EmployeeFilter onPage(int index) {
        return new EmployeeFilter(text, jobId, state, salaryKind, employmentType, hiredFrom, hiredTo,
                minRate, maxRate, delegatesOnly, index, pageSize);
    }

    /** The same filter without the two conditions a reader may not be allowed to move. */
    public EmployeeFilter withoutRateBounds() {
        return new EmployeeFilter(text, jobId, state, salaryKind, employmentType, hiredFrom, hiredTo,
                null, null, delegatesOnly, page, pageSize);
    }

    public EmployeeFilter withText(String value) {
        return new EmployeeFilter(value, jobId, state, salaryKind, employmentType, hiredFrom, hiredTo,
                minRate, maxRate, delegatesOnly, 0, pageSize);
    }

    public EmployeeFilter withState(EmployeeState value) {
        return new EmployeeFilter(text, jobId, value, salaryKind, employmentType, hiredFrom, hiredTo,
                minRate, maxRate, delegatesOnly, 0, pageSize);
    }

    public EmployeeFilter withJob(Integer value) {
        return new EmployeeFilter(text, value, state, salaryKind, employmentType, hiredFrom, hiredTo,
                minRate, maxRate, delegatesOnly, 0, pageSize);
    }

    public EmployeeFilter withSalaryKind(SalaryKind value) {
        return new EmployeeFilter(text, jobId, state, value, employmentType, hiredFrom, hiredTo,
                minRate, maxRate, delegatesOnly, 0, pageSize);
    }

    public EmployeeFilter withEmploymentType(EmploymentType value) {
        return new EmployeeFilter(text, jobId, state, salaryKind, value, hiredFrom, hiredTo,
                minRate, maxRate, delegatesOnly, 0, pageSize);
    }

    public EmployeeFilter withHired(LocalDate from, LocalDate to) {
        return new EmployeeFilter(text, jobId, state, salaryKind, employmentType, from, to,
                minRate, maxRate, delegatesOnly, 0, pageSize);
    }

    public EmployeeFilter withRateBounds(BigDecimal min, BigDecimal max) {
        return new EmployeeFilter(text, jobId, state, salaryKind, employmentType, hiredFrom, hiredTo,
                min, max, delegatesOnly, 0, pageSize);
    }

    public EmployeeFilter withDelegatesOnly(boolean value) {
        return new EmployeeFilter(text, jobId, state, salaryKind, employmentType, hiredFrom, hiredTo,
                minRate, maxRate, value, 0, pageSize);
    }

    /** Whether anything at all narrows the list - what the "clear filters" button is enabled by. */
    public boolean isNarrowed() {
        return hasText() || jobId != null || state != EmployeeState.ALL || salaryKind != null
                || employmentType != null || hiredFrom != null || hiredTo != null
                || filtersOnRate() || delegatesOnly;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EmployeeFilter f
                && text.equals(f.text) && Objects.equals(jobId, f.jobId) && state == f.state
                && salaryKind == f.salaryKind && employmentType == f.employmentType
                && Objects.equals(hiredFrom, f.hiredFrom) && Objects.equals(hiredTo, f.hiredTo)
                && Objects.equals(minRate, f.minRate) && Objects.equals(maxRate, f.maxRate)
                && delegatesOnly == f.delegatesOnly && page == f.page && pageSize == f.pageSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, jobId, state, salaryKind, employmentType, hiredFrom, hiredTo,
                minRate, maxRate, delegatesOnly, page, pageSize);
    }
}
