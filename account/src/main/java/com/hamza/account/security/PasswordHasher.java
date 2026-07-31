package com.hamza.account.security;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Also accepts legacy plaintext values so accounts created before bcrypt was
 * introduced keep working; {@link #matches} reports that case via
 * {@link Result#legacyPlaintext()} so the caller can re-hash and persist it.
 */
public final class PasswordHasher {

    private PasswordHasher() {
    }

    public static String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt());
    }

    public static Result matches(String plainPassword, String storedValue) {
        if (storedValue == null) return new Result(false, false);
        if (isBcryptHash(storedValue)) {
            return new Result(BCrypt.checkpw(plainPassword, storedValue), false);
        }
        boolean matched = storedValue.equals(plainPassword);
        return new Result(matched, matched);
    }

    private static boolean isBcryptHash(String value) {
        return value.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
    }

    public record Result(boolean matched, boolean legacyPlaintext) {
    }
}
