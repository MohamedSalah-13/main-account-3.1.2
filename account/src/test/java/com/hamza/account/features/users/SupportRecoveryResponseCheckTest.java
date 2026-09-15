package com.hamza.account.features.users;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportRecoveryResponseCheckTest {

    private static final LocalDateTime MOMENT = LocalDateTime.of(2026, 9, 7, 14, 5, 33);
    private static final SupportRecoveryChallenge ON_SCREEN = new SupportRecoveryChallenge("A1B2C3D4E5F60718", "PC-01", MOMENT);
    private static final SupportRecoveryChallenge EARLIER = new SupportRecoveryChallenge("0000000000000001", "PC-01", MOMENT);

    private static KeyPair pair;
    private static SupportRecoverySigner.Verifier trusted;

    @BeforeAll
    static void makeKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        pair = generator.generateKeyPair();
        trusted = (payload, signature) -> SupportRecoverySignerTest.verifies(pair.getPublic(), payload, signature);
    }

    @Test
    void eachWayAPasteCanGoWrongIsToldApart() throws Exception {
        String answer = new SupportRecoverySigner(trusted).sign(ON_SCREEN, pair.getPrivate());
        String answerToAnEarlierRequest = new SupportRecoverySigner(trusted).sign(EARLIER, pair.getPrivate());
        String forged = SupportRecoveryResponse.envelope(ON_SCREEN.signedText(), new byte[]{1, 2, 3});

        assertEquals(SupportRecoveryResponseCheck.EMPTY, SupportRecoveryResponseCheck.of("  ", ON_SCREEN, trusted));
        assertEquals(SupportRecoveryResponseCheck.INCOMPLETE,
                SupportRecoveryResponseCheck.of(answer.substring(0, answer.indexOf('.')), ON_SCREEN, trusted));
        assertEquals(SupportRecoveryResponseCheck.NOT_SIGNED, SupportRecoveryResponseCheck.of(forged, ON_SCREEN, trusted));
        assertEquals(SupportRecoveryResponseCheck.OTHER_REQUEST,
                SupportRecoveryResponseCheck.of(answerToAnEarlierRequest, ON_SCREEN, trusted));
        assertEquals(SupportRecoveryResponseCheck.MATCHES, SupportRecoveryResponseCheck.of(answer, ON_SCREEN, trusted));
    }

    /** A 590-character line pasted through a chat application arrives wrapped. */
    @Test
    void aWrappedResponseIsStillWhole() throws Exception {
        String answer = new SupportRecoverySigner(trusted).sign(ON_SCREEN, pair.getPrivate());
        String wrapped = String.join("\r\n", answer.split("(?<=\\G.{76})"));

        assertEquals(SupportRecoveryResponseCheck.MATCHES, SupportRecoveryResponseCheck.of(wrapped, ON_SCREEN, trusted));
    }

    /**
     * Only text with nothing in it to submit is held back; a signed response to the wrong request
     * still goes to the service, which records the refusal.
     */
    @Test
    void onlyWhatIsNotAResponseAtAllIsHeldBack() {
        assertFalse(SupportRecoveryResponseCheck.EMPTY.submittable());
        assertFalse(SupportRecoveryResponseCheck.INCOMPLETE.submittable());
        assertTrue(SupportRecoveryResponseCheck.NOT_SIGNED.submittable());
        assertTrue(SupportRecoveryResponseCheck.OTHER_REQUEST.submittable());
        assertTrue(SupportRecoveryResponseCheck.MATCHES.submittable());
    }

    @Test
    void noResponseAnswersAMissingChallenge() throws Exception {
        String answer = new SupportRecoverySigner(trusted).sign(ON_SCREEN, pair.getPrivate());

        assertEquals(SupportRecoveryResponseCheck.OTHER_REQUEST, SupportRecoveryResponseCheck.of(answer, null, trusted));
    }

    @Test
    void responseParsingRefusesWhatIsNotTwoBase64Halves() {
        for (String text : new String[]{null, "", ".", "abc.", ".abc", "a.b.c", "!!!.abc", "YWJj.!!!", "YWJj."}) {
            assertFalse(SupportRecoveryResponse.parse(text).isPresent(), String.valueOf(text));
        }
    }

    @Test
    void aResponseNamesItsNonceOnlyWhenItIsARecoveryPayload() {
        assertEquals("A1B2C3D4E5F60718", new SupportRecoveryResponse(ON_SCREEN.signedText(), new byte[1]).nonce().orElseThrow());
        assertFalse(new SupportRecoveryResponse("HAMZA_ACCOUNT|PC-01", new byte[1]).nonce().isPresent());
        assertFalse(new SupportRecoveryResponse("HAMZA_ACCOUNT|PC-01|N|T", new byte[1]).nonce().isPresent());
    }

    /**
     * The window and the signing tab reach these keys through a variable, so
     * MessageKeyArchitectureTest cannot see them - the PartyMovementKind arrangement.
     */
    @Test
    void everyKeyReachedThroughAVariableIsTranslatedAndFormatsItsArguments() throws Exception {
        for (String bundle : List.of("messages.properties", "messages_ar.properties", "messages_en.properties")) {
            Properties messages = new Properties();
            try (Reader reader = Files.newBufferedReader(
                    Path.of("../controlsfx/src/main/resources/i18n").resolve(bundle), StandardCharsets.UTF_8)) {
                messages.load(reader);
            }
            for (SupportRecoveryResponseCheck check : SupportRecoveryResponseCheck.values()) {
                assertNotNull(messages.getProperty(check.messageKey()), check.messageKey() + " in " + bundle);
            }
            for (SupportRecoveryRequestAge age : SupportRecoveryRequestAge.values()) {
                String message = messages.getProperty(age.messageKey());
                assertNotNull(message, age.messageKey() + " in " + bundle);
                String formatted = String.format(message, 7L, SupportRecoveryChallenge.VALID_FOR_MINUTES);
                assertTrue(formatted.contains("7") && !formatted.contains("%"), age.messageKey() + " in " + bundle);
            }
        }
    }

    /** Keeps the helper honest: a real signature over a changed payload is refused. */
    @Test
    void theTestVerifierRefusesAnEditedPayload() throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update(ON_SCREEN.signedText().getBytes(StandardCharsets.UTF_8));
        byte[] signature = signer.sign();

        assertTrue(trusted.verifies(ON_SCREEN.signedText(), signature));
        assertFalse(trusted.verifies(ON_SCREEN.signedText().replace("PC-01", "PC-02"), signature));
    }
}
