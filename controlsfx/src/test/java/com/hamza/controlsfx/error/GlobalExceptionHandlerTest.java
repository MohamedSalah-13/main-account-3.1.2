package com.hamza.controlsfx.error;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class GlobalExceptionHandlerTest {

    @Test
    void routesUnhandledTechnicalFailureThroughSafeReporter() {
        List<ErrorReport> presented = new ArrayList<>();
        ErrorReporter reporter = reporter();
        GlobalExceptionHandler handler = new GlobalExceptionHandler(
                reporter, presented::add, () -> "background refresh", null);

        handler.uncaughtException(Thread.currentThread(),
                new IllegalStateException("password=secret"));

        assertEquals(1, presented.size());
        ErrorReport report = presented.getFirst();
        assertEquals(ErrorCategory.TECHNICAL, report.category());
        assertEquals("ERR-TEST", report.referenceId());
        assertEquals("Safe background refresh ERR-TEST", report.message());
    }

    @Test
    void keepsClassifiedBusinessMessageAtTheGlobalBoundary() {
        List<ErrorReport> presented = new ArrayList<>();
        GlobalExceptionHandler handler = new GlobalExceptionHandler(
                reporter(), presented::add, () -> "saving", null);

        handler.uncaughtException(Thread.currentThread(),
                new BusinessRuleException("الفترة المحاسبية مغلقة"));

        ErrorReport report = presented.getFirst();
        assertEquals(ErrorCategory.BUSINESS, report.category());
        assertEquals("الفترة المحاسبية مغلقة", report.message());
    }

    @Test
    void preservesExistingHandlerAndOriginalFailure() {
        AtomicReference<Thread> delegatedThread = new AtomicReference<>();
        AtomicReference<Throwable> delegatedFailure = new AtomicReference<>();
        Thread.UncaughtExceptionHandler previous = (thread, failure) -> {
            delegatedThread.set(thread);
            delegatedFailure.set(failure);
        };
        GlobalExceptionHandler handler = new GlobalExceptionHandler(
                reporter(), report -> { }, () -> "working", previous);
        Thread thread = new Thread("worker");
        RuntimeException failure = new RuntimeException("boom");

        handler.uncaughtException(thread, failure);

        assertSame(thread, delegatedThread.get());
        assertSame(failure, delegatedFailure.get());
    }

    @Test
    void brokenPresenterCannotPreventPreviousHandler() {
        AtomicReference<Throwable> delegatedFailure = new AtomicReference<>();
        RuntimeException original = new RuntimeException("original");
        GlobalExceptionHandler handler = new GlobalExceptionHandler(
                reporter(),
                report -> { throw new IllegalStateException("presenter failed"); },
                () -> "working",
                (thread, failure) -> delegatedFailure.set(failure));

        handler.uncaughtException(Thread.currentThread(), original);

        assertSame(original, delegatedFailure.get());
    }

    private ErrorReporter reporter() {
        return new ErrorReporter(() -> "ERR-TEST", (key, arguments) -> switch (key) {
            case "error.validation.title" -> "Validation";
            case "error.business.title" -> "Business";
            case "error.unexpected.title" -> "Unexpected";
            case "error.unexpected.message" -> "Safe %s %s".formatted(arguments);
            default -> key;
        });
    }
}
