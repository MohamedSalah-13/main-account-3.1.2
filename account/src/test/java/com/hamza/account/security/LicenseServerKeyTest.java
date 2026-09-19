package com.hamza.account.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseServerKeyTest {

    /**
     * True today - there is no server yet - and the branch that matters the day the constant
     * is filled in: a build with no key refuses every signature rather than throwing on an
     * empty PEM.
     */
    @Test
    void aBlankKeyVerifiesNothingAndThrowsNothing() {
        if (LicenseServerKey.isConfigured()) {
            return;
        }
        assertFalse(LicenseServerKey.verifies("HAMZA_LICENSE2|PC-01|C-1|PRO|2026-10-01|2027-10-01|-",
                "anything".getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * The whole reason there are two keys is that they are two keys. Pasting the release
     * key's public half here would put the key that signs emergency recovery on a server
     * that faces the internet - and everything would go on working, which is why it is
     * checked rather than trusted.
     */
    @Test
    void onceConfiguredItIsNotTheReleaseKeyAndIsStrongEnough() throws Exception {
        if (!LicenseServerKey.isConfigured()) {
            return;
        }
        assertEquals("RSA", LicenseServerKey.publicKey().getAlgorithm());
        assertNotEquals(ReleaseSigningKey.publicKey(), LicenseServerKey.publicKey());
        assertTrue(((RSAPublicKey) LicenseServerKey.publicKey()).getModulus().bitLength() >= 3072);
    }

    @Test
    void rubbishIsRefusedRatherThanThrown() {
        assertFalse(LicenseServerKey.verifies("", new byte[0]));
        assertFalse(LicenseServerKey.verifies("anything", new byte[]{1, 2, 3}));
    }
}
