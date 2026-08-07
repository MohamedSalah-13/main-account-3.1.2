package com.hamza.account.security;

import lombok.extern.log4j.Log4j2;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Also accepts legacy plaintext values so accounts created before bcrypt was
 * introduced keep working; {@link #matches} reports that case via
 * {@link Result#legacyPlaintext()} so the caller can re-hash and persist it.
 */
@Log4j2
public final class PasswordHasher {

    private PasswordHasher() {
    }

    public static String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt());
    }

    public static Result matches(String plainPassword, String storedValue) {
        if (storedValue == null) return new Result(false, false);
        if (isBcryptHash(storedValue)) {
            try {
                return new Result(BCrypt.checkpw(plainPassword, storedValue), false);
            } catch (IllegalArgumentException e) {
                // isBcryptHash recognises the $2b$ and $2y$ revisions, which jBCrypt
                // cannot verify - it rejects them with "Invalid salt revision". Such a
                // hash reaches here only if it was written by something other than this
                // application, but letting the exception out turns a failed sign-in into
                // a crash. Refusing the attempt is the safe reading.
                log.error("Stored password hash cannot be verified: {}", e.getMessage());
                return new Result(false, false);
            }
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
