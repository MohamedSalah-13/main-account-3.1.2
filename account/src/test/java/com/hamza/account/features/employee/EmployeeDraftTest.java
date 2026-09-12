package com.hamza.account.features.employee;

import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The form's limits, which are the schema's, and the message key each refusal carries. */
class EmployeeDraftTest {

    private static final LocalDate HIRED = LocalDate.of(2026, 3, 1);

    private static EmployeeDraft parse(String name, int jobId, LocalDate birth, LocalDate hire,
                                       LocalDate end, BigDecimal rate) throws UserValidationException {
        return EmployeeDraft.parse(0, name, jobId, birth, hire, end, EmploymentType.FULL_TIME,
                "", "", "", "", "", null, SalaryKind.MONTHLY, rate);
    }

    private static String keyOf(Executable work) {
        return assertThrows(UserValidationException.class, work::run).getMessage();
    }

    private interface Executable {
        void run() throws Exception;
    }

    @Test
    @DisplayName("a refusal is a message key, never an Arabic sentence")
    void keysNotSentences() {
        assertEquals("employee.error.name", keyOf(() -> parse("  ", 1, null, HIRED, null, BigDecimal.TEN)));
        assertEquals("employee.error.job", keyOf(() -> parse("عمر", 0, null, HIRED, null, BigDecimal.TEN)));
        assertEquals("employee.error.hire.required",
                keyOf(() -> parse("عمر", 1, null, null, null, BigDecimal.TEN)));
    }

    @Test
    @DisplayName("a name is counted in code points, so fifty Arabic letters fit the fifty-character column")
    void nameLength() {
        String fifty = "ا".repeat(50);
        assertEquals(fifty, assertDoesNotThrow(() -> parse(fifty, 1, null, HIRED, null, BigDecimal.ONE)).name());
        assertEquals("employee.error.name.length",
                keyOf(() -> parse("ا".repeat(51), 1, null, HIRED, null, BigDecimal.ONE)));
    }

    @Test
    @DisplayName("the dates have to make sense against each other")
    void dates() {
        assertEquals("employee.error.birth.after.hire",
                keyOf(() -> parse("عمر", 1, HIRED, HIRED, null, BigDecimal.ONE)));
        assertEquals("employee.error.end.before.hire",
                keyOf(() -> parse("عمر", 1, null, HIRED, HIRED.minusDays(1), BigDecimal.ONE)));
    }

    @Test
    @DisplayName("a birth date is optional - it is data many shops simply do not hold")
    void birthDateOptional() {
        assertNull(assertDoesNotThrow(() -> parse("عمر", 1, null, HIRED, null, BigDecimal.ONE)).birthDate());
    }

    @Test
    @DisplayName("a salary is neither negative nor larger than DECIMAL(14, 2)")
    void rateBounds() {
        assertEquals("employee.error.salary.negative",
                keyOf(() -> parse("عمر", 1, null, HIRED, null, new BigDecimal("-1"))));
        assertEquals("employee.error.salary.range",
                keyOf(() -> parse("عمر", 1, null, HIRED, null, new BigDecimal("1000000000000"))));
    }

    @Test
    @DisplayName("a missing salary is zero rather than a failure, and is stored at two places")
    void rateDefaults() {
        EmployeeDraft draft = assertDoesNotThrow(() -> parse("عمر", 1, null, HIRED, null, null));
        assertEquals(new BigDecimal("0.00"), draft.rate());

        EmployeeDraft rounded = assertDoesNotThrow(
                () -> parse("عمر", 1, null, HIRED, null, new BigDecimal("1200.005")));
        assertEquals(new BigDecimal("1200.01"), rounded.rate(), "money rounds HALF_UP, here as everywhere");
    }

    @Test
    @DisplayName("blank contact details are stored as blanks, not as nulls with different meanings")
    void blanksAreStripped() {
        EmployeeDraft draft = assertDoesNotThrow(() -> EmployeeDraft.parse(0, "  عمر  ", 1, null,
                HIRED, null, null, "  123  ", null, null, null, null, null, null, BigDecimal.ONE));
        assertEquals("عمر", draft.name());
        assertEquals("123", draft.nationalId());
        assertEquals("", draft.email());
        assertEquals(EmploymentType.FULL_TIME, draft.employmentType(), "the ordinary case is the default");
        assertEquals(SalaryKind.MONTHLY, draft.salaryKind());
    }

    private static <T> T assertDoesNotThrow(java.util.concurrent.Callable<T> work) {
        try {
            return work.call();
        } catch (Exception e) {
            throw new AssertionError("unexpected refusal: " + e.getMessage(), e);
        }
    }
}
