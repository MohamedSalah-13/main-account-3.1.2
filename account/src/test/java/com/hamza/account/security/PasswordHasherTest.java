package com.hamza.account.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This decides who gets to log in, and it has to keep accepting the plaintext
 * passwords that predate bcrypt without ever treating a stored hash as one.
 */
class PasswordHasherTest {

    private static final String PASSWORD = "correct horse battery staple";

    @Nested
    @DisplayName("hashed passwords")
    class Hashed {

        @Test
        void acceptsTheRightPassword() {
            PasswordHasher.Result result = PasswordHasher.matches(PASSWORD, PasswordHasher.hash(PASSWORD));

            assertTrue(result.matched());
            assertFalse(result.legacyPlaintext(), "a bcrypt hash is not a legacy value");
        }

        @Test
        void rejectsTheWrongPassword() {
            assertFalse(PasswordHasher.matches("wrong", PasswordHasher.hash(PASSWORD)).matched());
        }

        @Test
        @DisplayName("the same password hashes differently every time - the salt is random")
        void hashingIsSalted() {
            String first = PasswordHasher.hash(PASSWORD);
            String second = PasswordHasher.hash(PASSWORD);

            assertNotEquals(first, second);
            assertTrue(PasswordHasher.matches(PASSWORD, first).matched());
            assertTrue(PasswordHasher.matches(PASSWORD, second).matched());
        }

        @Test
        void isCaseSensitive() {
            assertFalse(PasswordHasher.matches(PASSWORD.toUpperCase(), PasswordHasher.hash(PASSWORD)).matched());
        }

        @Test
        @DisplayName("typing the stored hash itself is not a way in")
        void theHashIsNotItsOwnPassword() {
            String stored = PasswordHasher.hash(PASSWORD);

            assertFalse(PasswordHasher.matches(stored, stored).matched());
        }

        /**
         * The shape test recognises the $2b$ and $2y$ revisions, but jBCrypt cannot
         * verify them and rejects them with "Invalid salt revision". Such a hash can
         * only arrive from outside this application, and it must fail the sign-in
         * rather than escape as an exception.
         */
        @Test
        @DisplayName("a hash revision jBCrypt cannot verify fails the sign-in instead of throwing")
        void unsupportedRevisionIsRefusedNotThrown() {
            for (String stored : new String[]{
                    "$2b$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                    "$2y$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"}) {

                PasswordHasher.Result result =
                        assertDoesNotThrow(() -> PasswordHasher.matches("secret", stored), stored);
                assertFalse(result.matched(), stored);
                assertFalse(result.legacyPlaintext(), "nothing to upgrade: " + stored);
            }
        }
    }

    @Nested
    @DisplayName("legacy plaintext values")
    class Legacy {

        @Test
        @DisplayName("still lets the user in, and asks the caller to re-hash")
        void acceptsAndFlagsForUpgrade() {
            PasswordHasher.Result result = PasswordHasher.matches("plain123", "plain123");

            assertTrue(result.matched());
            assertTrue(result.legacyPlaintext(), "the caller re-hashes and persists on this flag");
        }

        @Test
        void rejectsTheWrongOne() {
            PasswordHasher.Result result = PasswordHasher.matches("wrong", "plain123");

            assertFalse(result.matched());
            assertFalse(result.legacyPlaintext(), "nothing to upgrade when the password was wrong");
        }

        @Test
        @DisplayName("a value that merely looks bcrypt-ish is treated as plaintext")
        void shortBcryptLookalikeIsPlaintext() {
            // Right prefix, wrong length: not a hash, so compared literally.
            String lookalike = "$2a$10$tooshort";

            assertTrue(PasswordHasher.matches(lookalike, lookalike).matched());
            assertTrue(PasswordHasher.matches(lookalike, lookalike).legacyPlaintext());
        }
    }

    @Nested
    @DisplayName("absent or empty stored values")
    class Absent {

        @Test
        void nullStoredValueNeverMatches() {
            PasswordHasher.Result result = PasswordHasher.matches(PASSWORD, null);

            assertFalse(result.matched());
            assertFalse(result.legacyPlaintext());
        }

        @Test
        void aNonEmptyPasswordDoesNotMatchAnEmptyStoredValue() {
            assertFalse(PasswordHasher.matches(PASSWORD, "").matched());
        }

        /**
         * Documents today's behaviour rather than endorsing it: a user row whose
         * password column is empty can be logged into with an empty password,
         * because an empty stored value is treated as legacy plaintext and compared
         * literally. Whether an empty password should be rejected outright is a
         * decision for the account rules, not for this class.
         */
        @Test
        @DisplayName("an empty stored value is opened by an empty password")
        void emptyMatchesEmpty() {
            PasswordHasher.Result result = PasswordHasher.matches("", "");

            assertTrue(result.matched());
            assertTrue(result.legacyPlaintext());
        }
    }
}
