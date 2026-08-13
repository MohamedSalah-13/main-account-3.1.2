package com.hamza.account.features.invoice;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static com.hamza.account.features.invoice.InvoiceSaveValidator.Target.*;
import static org.junit.jupiter.api.Assertions.*;

class InvoiceSaveValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 13);

    @Test
    void acceptsCompleteInvoice() {
        assertTrue(validate(1, false, TODAY, true, true, true, 12).isEmpty());
    }

    @Test
    void rejectsMissingOrInvalidLinesBeforeOtherFields() {
        assertProblem(validate(0, false, null, true, false, false, 0),
                LINES, "بدون أصناف");
        assertProblem(validate(1, true, TODAY, false, true, true, 1),
                LINES, "صفر أو أقل");
    }

    @Test
    void rejectsMissingAndFutureDates() {
        assertProblem(validate(1, false, null, false, true, true, 1),
                DATE, "حدد تاريخ");
        assertProblem(validate(1, false, TODAY.plusDays(1), false, true, true, 1),
                DATE, "بعد تاريخ اليوم");
    }

    @Test
    void delegateIsRequiredOnlyForCustomerSide() {
        assertProblem(validate(1, false, TODAY, true, false, true, 1),
                DELEGATE, "حدد المندوب");
        assertTrue(validate(1, false, TODAY, false, false, true, 1).isEmpty());
    }

    @Test
    void rejectsMissingTreasuryAndAccount() {
        assertProblem(validate(1, false, TODAY, false, true, false, 1),
                TREASURY, "حدد الخزينة");
        assertProblem(validate(1, false, TODAY, false, true, true, 0),
                ACCOUNT, "بيانات الاسم");
    }

    private Optional<InvoiceSaveValidator.Problem> validate(
            int lines, boolean invalidLine, LocalDate date, boolean delegateRequired,
            boolean delegateSelected, boolean treasurySelected, int accountId) {
        return InvoiceSaveValidator.firstProblem(lines, invalidLine, date, TODAY,
                delegateRequired, delegateSelected, treasurySelected, accountId);
    }

    private void assertProblem(Optional<InvoiceSaveValidator.Problem> result,
                               InvoiceSaveValidator.Target target, String messagePart) {
        InvoiceSaveValidator.Problem problem = result.orElseThrow();
        assertEquals(target, problem.target());
        assertTrue(problem.message().contains(messagePart));
    }
}
