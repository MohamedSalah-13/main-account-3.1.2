package com.hamza.controlsfx.error;

import java.util.Objects;

/**
 * The safe, user-facing result of applying the exception policy.
 * <p>
 * Technical failures carry a reference id and never copy the original
 * throwable. Expected validation and business failures carry no reference id;
 * their message comes only from {@link UserFacingException}.
 */
public record ErrorReport(ErrorCategory category, String referenceId, String title, String message) {

    public ErrorReport {
        category = Objects.requireNonNull(category, "category");
        referenceId = referenceId == null ? "" : referenceId.trim();
        if (category == ErrorCategory.TECHNICAL && referenceId.isEmpty()) {
            throw new IllegalArgumentException("A technical error must have a referenceId");
        }
        title = requireText(title, "title");
        message = requireText(message, "message");
    }

    public boolean hasReferenceId() {
        return !referenceId.isEmpty();
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }
}
