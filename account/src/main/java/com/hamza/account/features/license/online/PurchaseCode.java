package com.hamza.account.features.license.online;

import java.util.Locale;
import java.util.Optional;

/**
 * The purchase code the vendor sends a customer: {@code AK-XXXX-XXXX-XXXX-XXXX} (the licence server's
 * {@code server-plan.md} §4.2). Two prefix letters for the product, then sixteen characters of Crockford
 * base32 - fifteen random and one check character, Luhn mod 32 over the fifteen.
 * <p>
 * <b>This is a copy of the server's {@code PurchaseCode}, and both halves are contract</b>: the tolerant
 * reading and the check. Repeating the check here is what lets a code typed with one wrong character be
 * answered "written wrong" before anything is sent, without a round trip or a counted attempt against the
 * server's limits. {@code PurchaseCodeTest} pins the same fixed example the server's test pins.
 */
public final class PurchaseCode {

    /** {@code accountk}'s prefix; the server looks a code up within its product alone. */
    public static final String PREFIX = "AK";
    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    static final int RANDOM_CHARS = 15;
    static final int BODY_CHARS = RANDOM_CHARS + 1;

    private PurchaseCode() {
    }

    /**
     * The canonical form - the prefix and sixteen characters, upper case, no separators - or empty when
     * the input cannot be a code. Tolerant as a code read over the telephone or pasted from WhatsApp needs:
     * dashes, spaces and case are ignored, {@code O} reads {@code 0}, {@code I} and {@code L} read
     * {@code 1}, and the prefix may be left out. The check character must match.
     */
    public static Optional<String> normalise(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String cleaned = input.replaceAll("[\\s\\-]", "").toUpperCase(Locale.ROOT)
                .replace('O', '0').replace('I', '1').replace('L', '1');
        String body;
        if (cleaned.length() == PREFIX.length() + BODY_CHARS && cleaned.startsWith(PREFIX)) {
            body = cleaned.substring(PREFIX.length());
        } else if (cleaned.length() == BODY_CHARS) {
            body = cleaned;
        } else {
            return Optional.empty();
        }
        for (int i = 0; i < body.length(); i++) {
            if (ALPHABET.indexOf(body.charAt(i)) < 0) {
                return Optional.empty();
            }
        }
        if (checkCharacter(body.substring(0, RANDOM_CHARS)) != body.charAt(RANDOM_CHARS)) {
            return Optional.empty();
        }
        return Optional.of(PREFIX + body);
    }

    /** Luhn mod 32 over the alphabet: catches every single wrong character and nearly every swapped pair. */
    static char checkCharacter(CharSequence random) {
        int n = ALPHABET.length();
        int factor = 2;
        int sum = 0;
        for (int i = random.length() - 1; i >= 0; i--) {
            int addend = factor * ALPHABET.indexOf(random.charAt(i));
            factor = factor == 2 ? 1 : 2;
            sum += addend / n + addend % n;
        }
        return ALPHABET.charAt((n - sum % n) % n);
    }
}
