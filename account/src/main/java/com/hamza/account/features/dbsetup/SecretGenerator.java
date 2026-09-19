package com.hamza.account.features.dbsetup;

import java.security.SecureRandom;

/**
 * Passwords for an install that nobody is going to type.
 * <p>
 * The installer this replaces shipped one populated data folder and one {@code config.xml} to
 * every customer, so every install had the same root password and the same application account.
 * A secret made here exists on one machine only: it is generated, used, written where
 * {@code docs/installer-plan.md} ق-٥ says, and never appears on a command line.
 * <p>
 * The alphabet leaves out everything that means something to a shell, to an ini file or to a
 * SQL literal - quotes, backslash, {@code #}, {@code ;}, space - so a secret can pass through
 * any of them unescaped. Sixty-six symbols over thirty-two places is about 193 bits.
 */
public final class SecretGenerator {

    public static final int LENGTH = 32;

    static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_+=";

    private final SecureRandom random;

    public SecretGenerator() {
        this(new SecureRandom());
    }

    SecretGenerator(SecureRandom random) {
        this.random = random;
    }

    public String next() {
        StringBuilder secret = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            secret.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return secret.toString();
    }
}
