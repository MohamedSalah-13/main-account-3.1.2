package com.hamza.account.features.license;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Turns the bytes of a licence file into a {@link LicenseDecision}. No file, no clock, no
 * database and no key of its own - each arrives as an argument, which is what lets every row
 * of the table in {@code docs/licensing-server-plan.md} decision 4 be a unit test.
 *
 * <p>The order of the checks is the order of what may be trusted. Nothing in the text is
 * believed before the signature is - not even the machine it names.
 */
public final class LicenseEvaluator {

    /** Whether a signature over a payload is the licence server's. */
    @FunctionalInterface
    public interface SignatureCheck {
        boolean verifies(String payload, byte[] signature);
    }

    private final SignatureCheck serverSignature;
    private final boolean serverKeyConfigured;

    /**
     * @param serverKeyConfigured false while the build carries no server key. It is asked
     *                            apart from the check itself so that "no key" can be told
     *                            from "wrong signature": the first is our state, the second
     *                            is the file's.
     */
    public LicenseEvaluator(SignatureCheck serverSignature, boolean serverKeyConfigured) {
        this.serverSignature = serverSignature;
        this.serverKeyConfigured = serverKeyConfigured;
    }

    /**
     * @param fileBytes the file, or null when there is none
     * @param machineId this computer's identity, or null or blank when it cannot be read
     */
    public LicenseDecision evaluate(byte[] fileBytes, String machineId, LocalDate today) {
        if (fileBytes == null) {
            return LicenseDecision.without(LicenseStatus.ABSENT, today);
        }
        Optional<LicenseEnvelope> envelope = LicenseEnvelope.parse(fileBytes);
        if (envelope.isEmpty()) {
            return LicenseDecision.without(LicenseStatus.MALFORMED, today);
        }
        if (!serverKeyConfigured) {
            return LicenseDecision.without(LicenseStatus.SERVER_KEY_MISSING, today);
        }
        if (!serverSignature.verifies(envelope.get().payload(), envelope.get().signature())) {
            return LicenseDecision.without(LicenseStatus.BAD_SIGNATURE, today);
        }
        // Signed by the server and still not a licence: the server signed some other text.
        // That is the case the tag exists for, and it is refused exactly like rubbish.
        Optional<LicenseTerms> terms = LicenseTerms.parse(envelope.get().payload());
        if (terms.isEmpty()) {
            return LicenseDecision.without(LicenseStatus.MALFORMED, today);
        }
        if (machineId == null || machineId.isBlank()) {
            return LicenseDecision.without(LicenseStatus.MACHINE_UNKNOWN, today);
        }
        if (!terms.get().machine().equals(machineId)) {
            return new LicenseDecision(LicenseStatus.OTHER_MACHINE, terms, today);
        }
        return new LicenseDecision(statusOn(terms.get(), today), terms, today);
    }

    private static LicenseStatus statusOn(LicenseTerms terms, LocalDate today) {
        if (terms.perpetual() || !today.isAfter(terms.expires())) {
            return LicenseStatus.ACTIVE;
        }
        LocalDate graceEnds = terms.expires().plusDays(LicenseDecision.GRACE_DAYS);
        return today.isAfter(graceEnds) ? LicenseStatus.READ_ONLY : LicenseStatus.GRACE;
    }
}
