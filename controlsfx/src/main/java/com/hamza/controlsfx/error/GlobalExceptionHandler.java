package com.hamza.controlsfx.error;

import com.hamza.controlsfx.language.LanguageManager;
import lombok.extern.log4j.Log4j2;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Last-resort boundary for failures that escape an application thread.
 * <p>
 * JavaFX tasks keep their exception in {@code Task#getException()}, so they
 * still need an explicit failed-state handler. This class covers everything
 * that actually reaches Java's uncaught-exception mechanism, including failures
 * escaping JavaFX event handlers and plain background threads.
 */
@Log4j2
public final class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final String OPERATION_KEY = "error.operation.uncaught";

    private final ErrorReporter reporter;
    private final Consumer<ErrorReport> presenter;
    private final Supplier<String> operation;
    private final Thread.UncaughtExceptionHandler previous;

    GlobalExceptionHandler(ErrorReporter reporter,
                           Consumer<ErrorReport> presenter,
                           Supplier<String> operation,
                           Thread.UncaughtExceptionHandler previous) {
        this.reporter = Objects.requireNonNull(reporter, "reporter");
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.previous = previous;
    }

    /** Installs the shared handler once while preserving any existing handler. */
    public static synchronized void install() {
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        if (current instanceof GlobalExceptionHandler) {
            return;
        }

        LanguageManager language = LanguageManager.getInstance();
        Thread.setDefaultUncaughtExceptionHandler(new GlobalExceptionHandler(
                ErrorReporter.shared(),
                new FxErrorPresenter()::present,
                () -> language.getString(OPERATION_KEY),
                current));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable failure) {
        try {
            presenter.accept(reporter.report(operation.get(), failure));
        } catch (Throwable reportingFailure) {
            // A broken reporter must not hide the failure that brought us here.
            log.error("The global exception boundary failed while handling an uncaught exception",
                    reportingFailure);
        } finally {
            notifyPrevious(thread, failure);
        }
    }

    private void notifyPrevious(Thread thread, Throwable failure) {
        if (previous == null) {
            return;
        }
        try {
            previous.uncaughtException(thread, failure);
        } catch (Throwable previousFailure) {
            log.error("The previous uncaught exception handler also failed", previousFailure);
        }
    }
}
