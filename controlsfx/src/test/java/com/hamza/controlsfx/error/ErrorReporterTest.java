package com.hamza.controlsfx.error;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorReporterTest {

    private final ErrorReporter reporter = new ErrorReporter(
            () -> "ERR-TEST1234",
            (key, arguments) -> switch (key) {
                case "error.operation.default" -> "default operation";
                case "error.validation.title" -> "Validation title";
                case "error.business.title" -> "Business title";
                case "error.unexpected.title" -> "Safe title";
                case "error.unexpected.message" ->
                        "Failed during %s; reference %s".formatted(arguments);
                case "employee.error.pay.amount" -> "اكتب مبلغًا أكبر من صفر";
                default -> key;
            });

    @Test
    void unexpectedFailureIsLoggedBehindAReferenceAndNotExposed() {
        var report = reporter.reportUnexpected(
                "loading customers",
                new RuntimeException("SELECT password FROM users"));

        assertEquals("ERR-TEST1234", report.referenceId());
        assertEquals(ErrorCategory.TECHNICAL, report.category());
        assertTrue(report.hasReferenceId());
        assertEquals("Safe title", report.title());
        assertTrue(report.message().contains("loading customers"));
        assertTrue(report.message().contains("ERR-TEST1234"));
        assertFalse(report.message().contains("SELECT password"));
        assertFalse(report.message().contains("RuntimeException"));
    }

    @Test
    void blankOperationUsesTheLocalizedDefault() {
        var report = reporter.reportUnexpected("  ", null);

        assertTrue(report.message().contains("default operation"));
    }

    @Test
    void validationFailureShowsOnlyItsApprovedMessageWithoutAReference() {
        var report = reporter.report("saving an invoice",
                new UserValidationException("اختر تاريخ الفاتورة"));

        assertEquals(ErrorCategory.VALIDATION, report.category());
        assertEquals("Validation title", report.title());
        assertEquals("اختر تاريخ الفاتورة", report.message());
        assertFalse(report.hasReferenceId());
    }

    @Test
    void wrappedBusinessFailureIsStillRecognizedAtTheUiBoundary() {
        var report = reporter.report("deleting a unit",
                new RuntimeException("wrapper detail",
                        new BusinessRuleException("الوحدة مستخدمة في فاتورة")));

        assertEquals(ErrorCategory.BUSINESS, report.category());
        assertEquals("Business title", report.title());
        assertEquals("الوحدة مستخدمة في فاتورة", report.message());
        assertFalse(report.message().contains("wrapper detail"));
        assertFalse(report.hasReferenceId());
    }

    @Test
    void ordinaryDaoExceptionRemainsTechnicalEvenWhenItsMessageLooksFriendly() {
        var report = reporter.report("loading data",
                new com.hamza.controlsfx.database.DaoException("SELECT secret FROM config"));

        assertEquals(ErrorCategory.TECHNICAL, report.category());
        assertFalse(report.message().contains("SELECT secret"));
        assertTrue(report.hasReferenceId());
    }

    @Test
    void aThrownMessageKeyReachesTheUserAsItsSentence() {
        // The employee payment screen refused an empty amount and the alert read
        // "employee.error.pay.amount" - the key itself. Services throw keys by rule
        // (docs/new-code-rules.md), and nothing was turning one back into a sentence.
        var report = reporter.report("paying an employee",
                new UserValidationException("employee.error.pay.amount"));

        assertEquals("اكتب مبلغًا أكبر من صفر", report.message());
        assertEquals(ErrorCategory.VALIDATION, report.category());
    }

    @Test
    void aKeyWithNoTranslationIsStillShownRatherThanSwallowed() {
        var report = reporter.report("x", new UserValidationException("no.such.key.anywhere"));
        assertEquals("no.such.key.anywhere", report.message());
    }

    @Test
    void aMessageWrittenOutInFullIsNeverTouched() {
        // An Arabic sentence cannot look like a dotted identifier, so the two screens that
        // already translate at the throw are unaffected.
        var report = reporter.report("x", new UserValidationException("اكتب اسم العميل أولًا"));
        assertEquals("اكتب اسم العميل أولًا", report.message());

        var english = reporter.report("x", new UserValidationException("Enter a customer first."));
        assertEquals("Enter a customer first.", english.message());
    }
}
