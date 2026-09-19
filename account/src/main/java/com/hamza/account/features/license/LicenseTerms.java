package com.hamza.account.features.license;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * What a server-issued licence says, which is the text the server signs:
 *
 * <pre>HAMZA_LICENSE2|machine|customerId|edition|issued|updatesUntil|expires</pre>
 *
 * <p><b>The tag is load-bearing</b>, for the reason {@code HAMZA_RECOVERY} is: a signature is
 * over a text, and a text that could be read as another kind of authorisation is a licence
 * for something nobody sold. The older {@code HAMZA_ACCOUNT|machine} is not this format and
 * is never parsed here - it stays with the code that has always read it.
 *
 * @param updatesUntil the last day a release may be built on and still be this customer's
 * @param expires      the last day of a subscription; {@code null} for a perpetual licence,
 *                     written {@code -} in the text
 */
public record LicenseTerms(String machine, String customerId, String edition,
                           LocalDate issued, LocalDate updatesUntil, LocalDate expires) {

    public static final String TAG = "HAMZA_LICENSE2";
    private static final String NO_EXPIRY = "-";
    private static final int FIELDS = 7;

    public LicenseTerms {
        requireField(machine, "machine");
        requireField(customerId, "customerId");
        requireField(edition, "edition");
        if (issued == null || updatesUntil == null) {
            throw new IllegalArgumentException("a licence carries its issue date and its updates date");
        }
    }

    public boolean perpetual() {
        return expires == null;
    }

    /** The exact text a signature is made over. {@link #parse} reads it back unchanged. */
    public String encode() {
        return String.join("|", TAG, machine, customerId, edition, issued.toString(),
                updatesUntil.toString(), expires == null ? NO_EXPIRY : expires.toString());
    }

    /**
     * Empty for anything that is not exactly this format. The count of fields is strict: a
     * format that grows gets a new tag, so an old build never half-understands a new licence.
     */
    public static Optional<LicenseTerms> parse(String payload) {
        if (payload == null) {
            return Optional.empty();
        }
        String[] parts = payload.split("\\|", -1);
        if (parts.length != FIELDS || !TAG.equals(parts[0])) {
            return Optional.empty();
        }
        try {
            LocalDate expires = NO_EXPIRY.equals(parts[6]) ? null : LocalDate.parse(parts[6]);
            return Optional.of(new LicenseTerms(parts[1], parts[2], parts[3],
                    LocalDate.parse(parts[4]), LocalDate.parse(parts[5]), expires));
        } catch (DateTimeParseException | IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static void requireField(String value, String name) {
        if (value == null || value.isBlank() || value.indexOf('|') >= 0) {
            throw new IllegalArgumentException("a licence needs a " + name + " without a separator in it");
        }
    }
}
