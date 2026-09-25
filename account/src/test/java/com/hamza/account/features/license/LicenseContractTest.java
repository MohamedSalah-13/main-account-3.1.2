package com.hamza.account.features.license;

import com.hamza.account.security.LicenseServerKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The licence files the licence server writes, read by this program's real evaluator.
 *
 * <p>The files and the public key are {@code src/test/resources/contract/}, copied byte for byte
 * from the server's repository, where its own {@code LicenseContractTest} reads the same bytes.
 * They were signed once with a key that was destroyed straight after, so neither repository can
 * sign anything with it. A change to the format on either side breaks the other side's test the
 * day the new files are copied - {@code server-plan.md} §7.3 there, {@code licensing-server-plan.md}
 * §9 here.
 */
class LicenseContractTest {

    private static final String MACHINE = "3f2a9c4e-8b1d-4e6f-a07c-5d9e2b1f4c83";
    private static final LocalDate ISSUED = LocalDate.of(2026, 9, 24);
    private static final LocalDate UPDATES_UNTIL = LocalDate.of(2027, 9, 24);
    private static final LocalDate EXPIRES = LocalDate.of(2027, 10, 24);

    private static final String PERPETUAL_TEXT =
            "HAMZA_LICENSE2|3f2a9c4e-8b1d-4e6f-a07c-5d9e2b1f4c83|C00042|full|2026-09-24|2027-09-24|-";
    private static final String SUBSCRIPTION_TEXT =
            "HAMZA_LICENSE2|3f2a9c4e-8b1d-4e6f-a07c-5d9e2b1f4c83|C00042|pos|2026-09-24|2027-09-24|2027-10-24";

    private final PublicKey testKey = publicKey(read("TEST-ONLY-license-server-public.pem"));
    private final LicenseEvaluator evaluator = new LicenseEvaluator(this::verifiesWithTestKey, true);
    private final byte[] perpetual = read("accountk-license-perpetual.dat");
    private final byte[] subscription = read("accountk-license-subscription.dat");

    @TempDir
    Path folder;

    @Test
    void thePerpetualFileIsActiveWithTheTermsTheServerWrote() {
        LicenseDecision decision = evaluator.evaluate(perpetual, MACHINE, ISSUED);

        assertEquals(LicenseStatus.ACTIVE, decision.status());
        assertTrue(decision.skipsTrial());
        assertTrue(decision.mayRecord());
        LicenseTerms terms = decision.terms().orElseThrow();
        assertEquals(MACHINE, terms.machine());
        assertEquals("C00042", terms.customerId());
        assertEquals("full", terms.edition());
        assertEquals(ISSUED, terms.issued());
        assertEquals(UPDATES_UNTIL, terms.updatesUntil());
        assertTrue(terms.perpetual());
        assertEquals(LicenseStatus.ACTIVE, evaluator.evaluate(perpetual, MACHINE, LocalDate.of(2040, 1, 1)).status());
    }

    @Test
    void theSubscriptionFileIsActiveWithTheTermsTheServerWrote() {
        LicenseDecision decision = evaluator.evaluate(subscription, MACHINE, ISSUED);

        assertEquals(LicenseStatus.ACTIVE, decision.status());
        LicenseTerms terms = decision.terms().orElseThrow();
        assertEquals("C00042", terms.customerId());
        assertEquals("pos", terms.edition());
        assertEquals(UPDATES_UNTIL, terms.updatesUntil());
        assertEquals(EXPIRES, terms.expires());
    }

    /** The server's README names 2027-10-30 as a day of grace; the edges are this program's rule. */
    @Test
    void theSubscriptionRunsIntoItsGraceAndThenReadOnly() {
        assertEquals(LicenseStatus.ACTIVE, evaluator.evaluate(subscription, MACHINE, EXPIRES).status());
        assertEquals(LicenseStatus.GRACE, evaluator.evaluate(subscription, MACHINE, LocalDate.of(2027, 10, 30)).status());
        LocalDate lastDayOfGrace = EXPIRES.plusDays(LicenseDecision.GRACE_DAYS);
        assertEquals(LicenseStatus.GRACE, evaluator.evaluate(subscription, MACHINE, lastDayOfGrace).status());

        LicenseDecision after = evaluator.evaluate(subscription, MACHINE, lastDayOfGrace.plusDays(1));
        assertEquals(LicenseStatus.READ_ONLY, after.status());
        assertTrue(after.skipsTrial(), "an ended subscription is never the trial's business");
    }

    @Test
    void aCopiedFileIsAnotherMachinesLicence() {
        LicenseDecision decision = evaluator.evaluate(perpetual, "11111111-2222-3333-4444-555555555555", ISSUED);
        assertEquals(LicenseStatus.OTHER_MACHINE, decision.status());
        assertFalse(decision.skipsTrial());
    }

    /** A byte changed in the signature, and one in the text after its tag: both are a bad signature, never a licence. */
    @Test
    void aChangedByteIsABadSignature() {
        String file = new String(perpetual, StandardCharsets.US_ASCII);
        int dot = file.indexOf('.');

        String inTheSignature = replaceAt(file, dot + 100);
        String inTheText = replaceAt(file, dot / 2);

        for (String changed : new String[]{inTheSignature, inTheText}) {
            byte[] bytes = changed.getBytes(StandardCharsets.US_ASCII);
            assertTrue(LicenseEnvelope.claimsServerFormat(bytes), "still claims the format, so no older reader sees it");
            LicenseDecision decision = evaluator.evaluate(bytes, MACHINE, ISSUED);
            assertEquals(LicenseStatus.BAD_SIGNATURE, decision.status());
            assertTrue(decision.terms().isEmpty());
        }
    }

    /** The text the server signed is this program's {@code LicenseTerms} written out, character for character. */
    @Test
    void theSignedTextIsLicenseTermsCharacterForCharacter() {
        assertEquals(PERPETUAL_TEXT, payloadOf(perpetual));
        assertEquals(SUBSCRIPTION_TEXT, payloadOf(subscription));
        assertEquals(PERPETUAL_TEXT, LicenseTerms.parse(PERPETUAL_TEXT).orElseThrow().encode());
        assertEquals(SUBSCRIPTION_TEXT, LicenseTerms.parse(SUBSCRIPTION_TEXT).orElseThrow().encode());
    }

    /** The envelope this program would write around the same text and signature is the server's file, byte for byte. */
    @Test
    void theEnvelopeIsTheServersByteForByte() {
        for (byte[] file : new byte[][]{perpetual, subscription}) {
            LicenseEnvelope envelope = LicenseEnvelope.parse(file).orElseThrow();
            assertArrayEquals(file, LicenseEnvelope.encode(envelope.payload(), envelope.signature())
                    .getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Where the start-up reads it: beside the configuration, judged by the service, never handed to the older reader. */
    @Test
    void theServiceAcceptsTheFileWhereTheStartUpReadsIt() throws IOException {
        Path config = Files.createDirectories(folder.resolve("config"));
        Path program = Files.createDirectories(folder.resolve("program"));
        LicenseFiles files = new LicenseFiles(config, program);
        files.write(perpetual);
        LicenseService service = new LicenseService(files, evaluator, () -> MACHINE);

        assertEquals(LicenseStatus.ACTIVE, service.check(() -> LicenseClock.of(ISSUED, null, null)).status());
        assertTrue(service.filesForOlderReader().isEmpty());
    }

    /**
     * The test key signs these files and nothing real. If it were ever pasted into the set a
     * release trusts, anybody holding these two files would be licensed on the machine they name.
     */
    @Test
    void theTestKeyIsNotAKeyTheProgramTrusts() {
        for (PublicKey trusted : LicenseServerKey.publicKeys()) {
            assertFalse(Arrays.equals(testKey.getEncoded(), trusted.getEncoded()));
        }
        for (byte[] file : new byte[][]{perpetual, subscription}) {
            LicenseEnvelope envelope = LicenseEnvelope.parse(file).orElseThrow();
            assertFalse(LicenseServerKey.verifies(envelope.payload(), envelope.signature()));
        }
    }

    private boolean verifiesWithTestKey(String payload, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(testKey);
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signature);
        } catch (Exception refused) {
            return false;
        }
    }

    private static String payloadOf(byte[] file) {
        return LicenseEnvelope.parse(file).orElseThrow().payload();
    }

    /** The character at {@code index} replaced by another base64 character, so the file still parses. */
    private static String replaceAt(String text, int index) {
        char replacement = text.charAt(index) == 'A' ? 'B' : 'A';
        return text.substring(0, index) + replacement + text.substring(index + 1);
    }

    private static PublicKey publicKey(byte[] pem) {
        String body = new String(pem, StandardCharsets.US_ASCII)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] read(String name) {
        try (InputStream in = LicenseContractTest.class.getClassLoader().getResourceAsStream("contract/" + name)) {
            assertNotNull(in, "contract/" + name + " is not on the test class path");
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
