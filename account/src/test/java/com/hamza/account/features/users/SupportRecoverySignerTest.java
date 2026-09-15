package com.hamza.account.features.users;

import com.hamza.account.security.ReleaseSigningKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Signs with a key pair made for the test and thrown away with it - never the release key,
 * whose private half this repository does not hold.
 */
class SupportRecoverySignerTest {

    private static final SupportRecoveryChallenge REQUEST =
            new SupportRecoveryChallenge("A1B2C3D4E5F60718", "PC-01", LocalDateTime.of(2026, 9, 7, 14, 5, 33));

    private static KeyPair pair;

    @TempDir
    Path folder;

    @BeforeAll
    static void makeKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        pair = generator.generateKeyPair();
    }

    @Test
    void theResponseIsTheLicenceShapeOverTheExactTextTheCustomerCompares() throws Exception {
        String response = trustingThisPair().sign(REQUEST, pair.getPrivate());

        SupportRecoveryResponse parsed = SupportRecoveryResponse.parse(response).orElseThrow();
        assertEquals("HAMZA_RECOVERY|PC-01|A1B2C3D4E5F60718|2026-09-07 14:05:33", parsed.payload());
        assertTrue(parsed.answers(REQUEST));
        assertTrue(verifies(pair.getPublic(), parsed.payload(), parsed.signature()));
    }

    /** The same file the README's openssl genpkey writes, read by the same loader profiles use. */
    @Test
    void aPkcs8KeyFileSigns() throws Exception {
        Path key = folder.resolve("private_key.pem");
        Files.writeString(key, "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n", StandardCharsets.US_ASCII);

        String response = trustingThisPair().sign(REQUEST, key);

        assertTrue(SupportRecoveryResponse.parse(response).orElseThrow().answers(REQUEST));
    }

    /**
     * The check the script never made: a key from another pair signs happily and produces a
     * response every customer's machine refuses - found out on the telephone, unless here.
     */
    @Test
    void aKeyTheReleaseDoesNotTrustIsRefusedBeforeAnythingIsHandedOver() {
        SupportRecoverySigner.SigningException refused = assertThrows(SupportRecoverySigner.SigningException.class,
                () -> SupportRecoverySigner.release().sign(REQUEST, pair.getPrivate()));

        assertEquals("support.recovery.signer.error.untrusted.key", refused.messageKey());
    }

    @Test
    void aPkcs1KeyIsNamedAsSuchRatherThanAsUnreadable() throws Exception {
        Path key = folder.resolve("rsa.pem");
        Files.writeString(key, "-----BEGIN RSA PRIVATE KEY-----\nMIIE\n-----END RSA PRIVATE KEY-----\n");

        SupportRecoverySigner.SigningException refused = assertThrows(SupportRecoverySigner.SigningException.class,
                () -> trustingThisPair().sign(REQUEST, key));

        assertEquals("support.recovery.signer.error.key.format", refused.messageKey());
    }

    @Test
    void aMissingOrBrokenKeyFileIsRefused() throws Exception {
        Path broken = folder.resolve("broken.pem");
        Files.writeString(broken, "-----BEGIN PRIVATE KEY-----\nnot base64 at all\n-----END PRIVATE KEY-----\n");

        for (Path key : new Path[]{folder.resolve("absent.pem"), broken, null}) {
            SupportRecoverySigner.SigningException refused = assertThrows(SupportRecoverySigner.SigningException.class,
                    () -> trustingThisPair().sign(REQUEST, key));
            assertEquals("support.recovery.signer.error.key", refused.messageKey());
        }
    }

    /** The customer's side verifies against the release key alone; a test key must not pass it. */
    @Test
    void theCustomersCheckDoesNotAcceptAResponseFromAnyOtherKey() throws Exception {
        SupportRecoveryResponse response = SupportRecoveryResponse
                .parse(trustingThisPair().sign(REQUEST, pair.getPrivate())).orElseThrow();

        assertFalse(ReleaseSigningKey.verifies(response.payload(), response.signature()));
        assertEquals(SupportRecoveryResponseCheck.NOT_SIGNED, SupportRecoveryResponseCheck.of(
                SupportRecoveryResponse.envelope(response.payload(), response.signature()), REQUEST,
                ReleaseSigningKey::verifies));
    }

    private static SupportRecoverySigner trustingThisPair() {
        return new SupportRecoverySigner((payload, signature) -> verifies(pair.getPublic(), payload, signature));
    }

    static boolean verifies(PublicKey key, String payload, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(key);
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signature);
        } catch (Exception refused) {
            return false;
        }
    }
}
