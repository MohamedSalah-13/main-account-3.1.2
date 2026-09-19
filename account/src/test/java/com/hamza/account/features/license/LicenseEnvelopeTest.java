package com.hamza.account.features.license;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseEnvelopeTest {

    private static final String TERMS = "HAMZA_LICENSE2|PC-01|C-0042|PRO|2026-10-01|2027-10-01|-";

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void whatIsEncodedIsReadBack() {
        byte[] signature = {1, 2, 3, 4};
        LicenseEnvelope envelope = LicenseEnvelope.parse(bytes(LicenseEnvelope.encode(TERMS, signature))).orElseThrow();
        assertEquals(TERMS, envelope.payload());
        assertArrayEquals(signature, envelope.signature());
    }

    /** Notepad adds a BOM and a mail client a trailing newline; neither makes a licence invalid. */
    @Test
    void aBomAndSurroundingWhitespaceAreTolerated() {
        String text = "﻿  " + LicenseEnvelope.encode(TERMS, new byte[]{9}) + "\r\n";
        assertEquals(TERMS, LicenseEnvelope.parse(bytes(text)).orElseThrow().payload());
    }

    @Test
    void anythingElseIsEmpty() {
        assertTrue(LicenseEnvelope.parse(null).isEmpty());
        assertTrue(LicenseEnvelope.parse(new byte[0]).isEmpty());
        assertTrue(LicenseEnvelope.parse(bytes("no dot at all")).isEmpty());
        assertTrue(LicenseEnvelope.parse(bytes(".c2ln")).isEmpty());
        assertTrue(LicenseEnvelope.parse(bytes("cGF5.")).isEmpty());
        assertTrue(LicenseEnvelope.parse(bytes("cGF5.c2ln.extra")).isEmpty());
        assertTrue(LicenseEnvelope.parse(bytes("not base64!.c2ln")).isEmpty());
    }

    /**
     * This is the question that keeps a server-issued file away from the older reader, which
     * would end the install over it. It is asked of the tag alone, before any signature.
     */
    @Test
    void onlyTheServerTagClaimsTheServerFormat() {
        assertTrue(LicenseEnvelope.claimsServerFormat(bytes(LicenseEnvelope.encode(TERMS, new byte[]{1}))));
        assertFalse(LicenseEnvelope.claimsServerFormat(bytes(LicenseEnvelope.encode("HAMZA_ACCOUNT|PC-01", new byte[]{1}))));
        assertFalse(LicenseEnvelope.claimsServerFormat(bytes(LicenseEnvelope.encode("HAMZA_LICENSE2X|PC-01", new byte[]{1}))));
        assertFalse(LicenseEnvelope.claimsServerFormat(bytes("rubbish")));
        assertFalse(LicenseEnvelope.claimsServerFormat(new byte[0]));
    }
}
