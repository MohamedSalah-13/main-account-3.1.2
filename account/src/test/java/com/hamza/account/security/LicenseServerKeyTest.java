package com.hamza.account.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseServerKeyTest {

    /**
     * The fingerprints the licence server published for its keys, in {@link LicenseServerKey#PEMS}
     * order: SHA-256 over the DER, as the server's signing-keys page and {@code openssl} print it.
     *
     * <p>Pinned because a key pasted with one character wrong can still be a valid RSA key - just
     * not the server's - and a build carrying it would refuse every licence ever issued. A key is
     * added here from the server's page, never from the constant beside it.
     */
    private static final List<String> PUBLISHED_FINGERPRINTS = List.of(
            "c6f04c78492516ed7ad121e2991d4fc6c67d009ffbe78fed87a15ceede4d9358");

    @Test
    void everyKeyIsTheOneTheServerPublished() throws Exception {
        assertEquals(PUBLISHED_FINGERPRINTS.size(), LicenseServerKey.PEMS.size());
        for (int i = 0; i < LicenseServerKey.PEMS.size(); i++) {
            assertEquals(PUBLISHED_FINGERPRINTS.get(i), fingerprint(LicenseServerKey.derOf(LicenseServerKey.PEMS.get(i))),
                    "key " + i + " is not the key the server published");
        }
    }

    /** A key that does not parse is left out at run time, quietly; here it is named. */
    @Test
    void everyKeyParses() {
        assertEquals(LicenseServerKey.PEMS.size(), LicenseServerKey.publicKeys().size());
        assertTrue(LicenseServerKey.isConfigured());
    }

    /**
     * The whole reason there are two kinds of key is that they are different keys. Pasting the
     * release key's public half here would put the key that signs emergency recovery on a server
     * that faces the internet - and everything would go on working, which is why it is checked
     * rather than trusted.
     */
    @Test
    void everyKeyIsRsaStrongEnoughAndNotTheReleaseKey() throws Exception {
        for (PublicKey key : LicenseServerKey.publicKeys()) {
            assertEquals("RSA", key.getAlgorithm());
            assertTrue(((RSAPublicKey) key).getModulus().bitLength() >= 3072);
            assertNotEquals(ReleaseSigningKey.publicKey(), key);
        }
    }

    @Test
    void noKeyIsListedTwice() throws Exception {
        Set<String> seen = new HashSet<>();
        for (String pem : LicenseServerKey.PEMS) {
            assertTrue(seen.add(fingerprint(LicenseServerKey.derOf(pem))), "a key is listed twice");
        }
    }

    /**
     * Decision س-10, the reason this is a set: a file signed before a rotation still verifies on a
     * build carrying both keys, and stops verifying only on the build that drops the old one.
     * The keys are made here and thrown away with the JVM - no private key is kept anywhere.
     */
    @Test
    void aFileSignedWithTheRetiredKeyVerifiesWhileTheKeyIsInTheSet() throws Exception {
        KeyPair retired = rsa();
        KeyPair current = rsa();
        String payload = "HAMZA_LICENSE2|PC-01|C00042|full|2026-09-25|2027-09-25|-";
        byte[] signedBeforeTheRotation = sign(retired, payload);

        assertTrue(LicenseServerKey.verifiesWithAny(
                List.of(current.getPublic(), retired.getPublic()), payload, signedBeforeTheRotation));
        assertFalse(LicenseServerKey.verifiesWithAny(
                List.of(current.getPublic()), payload, signedBeforeTheRotation));
        assertFalse(LicenseServerKey.verifiesWithAny(List.of(), payload, signedBeforeTheRotation));
    }

    @Test
    void anEditedTextVerifiesWithNoKey() throws Exception {
        KeyPair key = rsa();
        byte[] signature = sign(key, "HAMZA_LICENSE2|PC-01|C00042|full|2026-09-25|2027-09-25|2027-10-25");
        assertFalse(LicenseServerKey.verifiesWithAny(List.of(key.getPublic()),
                "HAMZA_LICENSE2|PC-01|C00042|full|2026-09-25|2027-09-25|2099-12-31", signature));
    }

    @Test
    void rubbishIsRefusedRatherThanThrown() {
        assertFalse(LicenseServerKey.verifies("", new byte[0]));
        assertFalse(LicenseServerKey.verifies("anything", new byte[]{1, 2, 3}));
        assertFalse(LicenseServerKey.verifies(null, new byte[]{1, 2, 3}));
        assertFalse(LicenseServerKey.verifies("anything", null));
    }

    private static String fingerprint(byte[] der) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(der));
    }

    private static KeyPair rsa() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static byte[] sign(KeyPair key, String payload) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(key.getPrivate());
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        return signer.sign();
    }
}
