package com.hamza.controlsfx.error;

import com.hamza.controlsfx.language.LanguageManager;
import lombok.extern.log4j.Log4j2;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Records an unexpected failure and produces the safe text a user may see.
 * <p>
 * Exception messages are deliberately not copied into the report. A JDBC
 * driver message can contain SQL and schema details, and an arbitrary runtime
 * exception can contain paths or implementation names. The complete throwable
 * is kept in the log under {@link ErrorReport#referenceId()} instead.
 */
@Log4j2
public final class ErrorReporter {

    private static final String DEFAULT_OPERATION_KEY = "error.operation.default";
    private static final String VALIDATION_TITLE_KEY = "error.validation.title";
    private static final String BUSINESS_TITLE_KEY = "error.business.title";
    private static final String TITLE_KEY = "error.unexpected.title";
    private static final String MESSAGE_KEY = "error.unexpected.message";
    private static final ErrorReporter SHARED = new ErrorReporter(
            ErrorReporter::newReferenceId,
            LanguageManager.getInstance()::getString);

    private final Supplier<String> referenceIds;
    /** A dotted lower-case identifier, the shape MessageKeyArchitectureTest pins. */
    private static final java.util.regex.Pattern KEY_SHAPE =
            java.util.regex.Pattern.compile("[a-z][a-z0-9]*(?:\\.[a-z0-9]+)+");

    private final MessageResolver messages;

    public static ErrorReporter shared() {
        return SHARED;
    }

    ErrorReporter(Supplier<String> referenceIds, MessageResolver messages) {
        this.referenceIds = Objects.requireNonNull(referenceIds, "referenceIds");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Applies the exception policy at a UI boundary. Expected failures expose
     * only their explicitly approved user message; everything else takes the
     * technical path with a reference id.
     */
    public ErrorReport report(String operation, Throwable failure) {
        UserFacingException expected = findExpectedFailure(failure);
        if (expected == null) {
            return reportUnexpected(operation, failure);
        }

        String safeOperation = normalizeOperation(operation);
        String message = expected.userMessage();
        ErrorCategory category = expected.category();
        if (category == ErrorCategory.TECHNICAL || message == null || message.isBlank()) {
            return reportUnexpected(safeOperation, failure);
        }

        if (category == ErrorCategory.VALIDATION) {
            log.info("Validation rejected during {}: {}", safeOperation, message);
        } else {
            log.warn("Business rule rejected during {}: {}", safeOperation, message);
        }

        String titleKey = category == ErrorCategory.VALIDATION
                ? VALIDATION_TITLE_KEY
                : BUSINESS_TITLE_KEY;
        return new ErrorReport(category, "", messages.get(titleKey), resolve(message));
    }

    /**
     * Logs {@code failure} once and returns a localized report containing no
     * technical details from it.
     */
    /**
     * A message key becomes its sentence; anything else is passed through untouched.
     * <p>
     * {@code docs/new-code-rules.md} says a service throws a message <i>key</i>, never an Arabic
     * literal, and nothing on the way to the screen was turning one back into a sentence: the
     * employee payment screen refused an empty amount with the text
     * {@code employee.error.pay.amount} in the alert, which is what a user read. It could not be
     * caught by a build - {@code MessageKeyArchitectureTest} reads the arguments of
     * {@code getString}/{@code text}/{@code Columns.*}, and a key handed to an exception
     * constructor is none of those - and the two screens that already threw keys each translated
     * them their own way, one at the throw and one at the display.
     * <p>
     * The test is the shape of the string: a dotted lower-case identifier is a key and an Arabic
     * sentence can never look like one, so a message written out in full is never touched.
     */
    private String resolve(String message) {
        if (!KEY_SHAPE.matcher(message).matches()) {
            return message;
        }
        String resolved = messages.get(message);
        return resolved == null || resolved.isBlank() ? message : resolved;
    }

    public ErrorReport reportUnexpected(String operation, Throwable failure) {
        String referenceId = normalizedReference(referenceIds.get());
        String safeOperation = normalizeOperation(operation);
        Throwable actualFailure = failure == null
                ? new IllegalStateException("No throwable was supplied to ErrorReporter")
                : failure;

        log.error("Unexpected failure [{}] during {}", referenceId, safeOperation, actualFailure);

        return new ErrorReport(
                ErrorCategory.TECHNICAL,
                referenceId,
                messages.get(TITLE_KEY),
                messages.get(MESSAGE_KEY, safeOperation, referenceId));
    }

    private String normalizeOperation(String operation) {
        return operation == null || operation.isBlank()
                ? messages.get(DEFAULT_OPERATION_KEY)
                : operation.trim();
    }

    private static UserFacingException findExpectedFailure(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current != null && visited.add(current)) {
            if (current instanceof UserFacingException expected) {
                return expected;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String newReferenceId() {
        return "ERR-" + UUID.randomUUID().toString()
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
    }

    private static String normalizedReference(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            return newReferenceId();
        }
        return referenceId.trim();
    }

    @FunctionalInterface
    interface MessageResolver {
        String get(String key, Object... arguments);
    }

}
