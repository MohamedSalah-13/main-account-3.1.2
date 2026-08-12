package com.hamza.account.authorization;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable, database-id-independent authorization key. */
public record PermissionKey(String value) {

    private static final Pattern VALID = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+");
    private static final PermissionKey DENY = new PermissionKey("system.denied");
    private static final PermissionKey PUBLIC = new PermissionKey("system.public");

    public PermissionKey {
        value = Objects.requireNonNull(value, "permission key").trim();
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid permission key: " + value);
        }
    }

    public static PermissionKey of(String value) {
        return new PermissionKey(value);
    }

    /** Explicit deny marker for unavailable features; it is never persisted. */
    public static PermissionKey deny() {
        return DENY;
    }

    /** Explicit marker for actions available without an authorization grant. */
    public static PermissionKey publicAccess() {
        return PUBLIC;
    }

    public boolean isDenyMarker() {
        return DENY.value.equals(value);
    }

    public boolean isPublicMarker() {
        return PUBLIC.value.equals(value);
    }

    public boolean isMarker() {
        return isDenyMarker() || isPublicMarker();
    }

    @Override
    public String toString() {
        return value;
    }
}
