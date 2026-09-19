package com.hamza.account.features.license;

import com.hamza.account.security.ReleaseSigningKey;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Decision 4 of {@code docs/licensing-server-plan.md}, a row at a time. */
class LicenseEvaluatorTest {

    private static final String MACHINE = "PC-01";
    private static final LocalDate ISSUED = LocalDate.of(2026, 10, 1);
    private static final LocalDate UPDATES_UNTIL = LocalDate.of(2027, 10, 1);
    private static final LocalDate EXPIRES = LocalDate.of(2027, 9, 30);

    private final TestLicenceSigner server = new TestLicenceSigner();
    private final LicenseEvaluator evaluator = server.evaluator();

    private static LicenseTerms perpetual() {
        return new LicenseTerms(MACHINE, "C-0042", "PRO", ISSUED, UPDATES_UNTIL, null);
    }

    private static LicenseTerms subscription() {
        return new LicenseTerms(MACHINE, "C-0042", "PRO", ISSUED, UPDATES_UNTIL, EXPIRES);
    }

    private LicenseDecision on(LicenseTerms terms, LocalDate today) {
        return evaluator.evaluate(server.file(terms), MACHINE, today);
    }

    @Test
    void noFileIsAbsent() {
        assertEquals(LicenseStatus.ABSENT, evaluator.evaluate(null, MACHINE, ISSUED).status());
    }

    @Test
    void aPerpetualLicenceIsActiveForEver() {
        LicenseDecision decision = on(perpetual(), LocalDate.of(2040, 1, 1));
        assertEquals(LicenseStatus.ACTIVE, decision.status());
        assertTrue(decision.skipsTrial());
        assertTrue(decision.mayRecord());
        assertTrue(decision.graceEnds().isEmpty());
    }

    /**
     * Updates running out changes what may be installed, never what may be done: the
     * program goes on working whole. Only a release built after the date is refused.
     */
    @Test
    void lapsedUpdatesLeaveTheProgramWhole() {
        LicenseDecision decision = on(perpetual(), UPDATES_UNTIL.plusYears(3));
        assertEquals(LicenseStatus.ACTIVE, decision.status());
        assertTrue(decision.mayRecord());
        assertTrue(decision.updatesCover(UPDATES_UNTIL));
        assertFalse(decision.updatesCover(UPDATES_UNTIL.plusDays(1)));
    }

    /** It is the build date that is compared - a release built in time is owed whenever it is installed. */
    @Test
    void aReleaseBuiltInTimeIsCoveredHoweverLateItIsInstalled() {
        assertTrue(on(perpetual(), UPDATES_UNTIL.plusYears(1)).updatesCover(UPDATES_UNTIL.minusMonths(2)));
    }

    @Test
    void aSubscriptionIsActiveThroughItsLastDay() {
        assertEquals(LicenseStatus.ACTIVE, on(subscription(), EXPIRES).status());
    }

    @Test
    void theDayAfterItEndsTheGraceBegins() {
        LicenseDecision decision = on(subscription(), EXPIRES.plusDays(1));
        assertEquals(LicenseStatus.GRACE, decision.status());
        assertTrue(decision.mayRecord());
        assertEquals(LicenseDecision.GRACE_DAYS, decision.graceDaysLeft());
    }

    @Test
    void theLastDayOfGraceStillRecords() {
        LicenseDecision decision = on(subscription(), EXPIRES.plusDays(LicenseDecision.GRACE_DAYS));
        assertEquals(LicenseStatus.GRACE, decision.status());
        assertEquals(1, decision.graceDaysLeft());
        assertTrue(decision.mayRecord());
    }

    /**
     * The row that matters most. Read-only, and <b>still skipping the trial</b>: sent down
     * the trial path this customer's two-year-old installation date reads as an expired
     * trial, which is a charged failure, and one is all an install is allowed.
     */
    @Test
    void afterTheGraceItIsReadOnlyAndStillNotTheTrialsBusiness() {
        LicenseDecision decision = on(subscription(), EXPIRES.plusDays(LicenseDecision.GRACE_DAYS + 1));
        assertEquals(LicenseStatus.READ_ONLY, decision.status());
        assertFalse(decision.mayRecord());
        assertTrue(decision.skipsTrial());
        assertEquals(0, decision.graceDaysLeft());

        LicenseDecision yearsLater = on(subscription(), EXPIRES.plusYears(5));
        assertEquals(LicenseStatus.READ_ONLY, yearsLater.status());
        assertTrue(yearsLater.skipsTrial());
    }

    /** A copied program folder. Genuine, not ours, and nobody's fault. */
    @Test
    void aLicenceForAnotherMachineIsNotThisMachinesAndKeepsItsTerms() {
        LicenseDecision decision = evaluator.evaluate(server.file(perpetual()), "PC-02", ISSUED);
        assertEquals(LicenseStatus.OTHER_MACHINE, decision.status());
        assertFalse(decision.skipsTrial());
        assertEquals("C-0042", decision.terms().orElseThrow().customerId());
    }

    @Test
    void anUnknownMachineMatchesNothing() {
        assertEquals(LicenseStatus.MACHINE_UNKNOWN, evaluator.evaluate(server.file(perpetual()), null, ISSUED).status());
        assertEquals(LicenseStatus.MACHINE_UNKNOWN, evaluator.evaluate(server.file(perpetual()), " ", ISSUED).status());
    }

    /** A subscription extended by editing the text: the signature is over the old text. */
    @Test
    void anEditedTextIsABadSignature() {
        String honest = subscription().encode();
        String edited = honest.replace(EXPIRES.toString(), "2099-12-31");
        byte[] forged = LicenseEnvelope.encode(edited, server.sign(honest)).getBytes(StandardCharsets.UTF_8);
        LicenseDecision decision = evaluator.evaluate(forged, MACHINE, ISSUED);
        assertEquals(LicenseStatus.BAD_SIGNATURE, decision.status());
        assertTrue(decision.terms().isEmpty(), "nothing in an unsigned text is believed, its terms included");
    }

    @Test
    void anotherServersSignatureIsABadSignature() {
        byte[] foreign = new TestLicenceSigner().file(perpetual());
        assertEquals(LicenseStatus.BAD_SIGNATURE, evaluator.evaluate(foreign, MACHINE, ISSUED).status());
    }

    /**
     * The tag, from the server's side: a text the server genuinely signed that is not
     * licence terms - the older licence text, say - licenses nothing.
     */
    @Test
    void aGenuineSignatureOverAnotherTextIsNotALicence() {
        LicenseDecision decision = evaluator.evaluate(server.file("HAMZA_ACCOUNT|" + MACHINE), MACHINE, ISSUED);
        assertEquals(LicenseStatus.MALFORMED, decision.status());
        assertFalse(decision.skipsTrial());
    }

    /**
     * The tag, from the release key's side: server-format terms are checked with the server
     * key <b>only</b>. The production wiring never consults {@link ReleaseSigningKey} here,
     * so the offline key cannot mint a subscription and the online key cannot mint anything
     * the offline one guards.
     */
    @Test
    void theReleaseKeyDoesNotVouchForServerTerms() {
        assertFalse(ReleaseSigningKey.verifies(perpetual().encode(), server.sign(perpetual().encode())));
    }

    /** A build with no server key accepts no server licence, and says why rather than "forged". */
    @Test
    void withoutAServerKeyNothingIsAcceptedAndNothingIsCalledForged() {
        LicenseEvaluator keyless = new LicenseEvaluator((payload, signature) -> false, false);
        LicenseDecision decision = keyless.evaluate(server.file(perpetual()), MACHINE, ISSUED);
        assertEquals(LicenseStatus.SERVER_KEY_MISSING, decision.status());
        assertFalse(decision.skipsTrial());
    }

    @Test
    void rubbishIsMalformed() {
        assertEquals(LicenseStatus.MALFORMED,
                evaluator.evaluate("half a fi".getBytes(StandardCharsets.UTF_8), MACHINE, ISSUED).status());
        assertEquals(LicenseStatus.MALFORMED, evaluator.evaluate(new byte[0], MACHINE, ISSUED).status());
    }

    /** Every refusal is the same kind of answer: not licensed by this file. None is a failure to charge. */
    @Test
    void onlyTheThreeLicensedStatesSkipTheTrial() {
        for (LicenseStatus status : LicenseStatus.values()) {
            boolean licensed = status == LicenseStatus.ACTIVE || status == LicenseStatus.GRACE
                    || status == LicenseStatus.READ_ONLY;
            assertEquals(licensed, status.skipsTrial(), status.name());
        }
    }
}
