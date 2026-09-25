package com.hamza.account.features.license;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which files are the older reader's to see. The shapes that must say yes are the ones that reader
 * accepts; the ones that must say no are what used to reach it and cost an install its one failure.
 */
class OlderLicenceFileTest {

    private static final String PAYLOAD_HALF =
            Base64.getEncoder().encodeToString("HAMZA_ACCOUNT|PC-01".getBytes(StandardCharsets.UTF_8));

    @Test
    void theOlderLicenceIsItsOwn() {
        assertTrue(OlderLicenceFile.claims(ascii(PAYLOAD_HALF + ".AQID")));
    }

    /** Hand-made with OpenSSL for years, so every shape the older reader tolerates must still be recognised. */
    @Test
    void theShapesTheOlderReaderToleratesAreRecognised() {
        assertTrue(OlderLicenceFile.claims(("﻿" + PAYLOAD_HALF + ".AQID").getBytes(StandardCharsets.UTF_8)), "a byte-order mark");
        assertTrue(OlderLicenceFile.claims(ascii(" \t" + PAYLOAD_HALF + "\r\n.AQID\r\n")), "whitespace around the text");
        assertTrue(OlderLicenceFile.claims(concat(ascii(PAYLOAD_HALF + "."), new byte[]{'.', '\n', (byte) 0xFF, 0})),
                "a signature written as raw bytes, dots and all");
        assertTrue(OlderLicenceFile.claims(ascii(PAYLOAD_HALF + ".")), "no signature: still its own, and its own to refuse");
    }

    /** Tampering with an older licence stays the older reader's to call tampering. */
    @Test
    void anEditedMachineIsStillTheOlderLicence() {
        String edited = Base64.getEncoder().encodeToString("HAMZA_ACCOUNT|SOMEBODY-ELSE".getBytes(StandardCharsets.UTF_8));
        assertTrue(OlderLicenceFile.claims(ascii(edited + ".AQID")));
    }

    @Test
    void aServerLicenceIsNot() {
        String terms = new LicenseTerms("PC-01", "C00042", "full", LocalDate.of(2026, 9, 25),
                LocalDate.of(2027, 9, 25), null).encode();
        assertFalse(OlderLicenceFile.claims(ascii(LicenseEnvelope.encode(terms, new byte[]{1, 2, 3}))));
    }

    /** The case that used to end an install: a server file whose first character - part of its tag - was changed. */
    @Test
    void aServerLicenceWithItsTagDamagedIsNot() {
        String terms = new LicenseTerms("PC-01", "C00042", "full", LocalDate.of(2026, 9, 25),
                LocalDate.of(2027, 9, 25), null).encode();
        String file = LicenseEnvelope.encode(terms, new byte[]{1, 2, 3});
        assertFalse(OlderLicenceFile.claims(ascii((file.charAt(0) == 'S' ? 'T' : 'S') + file.substring(1))));
    }

    @Test
    void anotherTagIsNot() {
        for (String text : new String[]{"HAMZA_ACCOUNt|PC-01", "HAMZA_ACCOUNT", "HAMZA_ACCOUNTX|PC-01", "HAMZA_RECOVERY|PC-01|1|2"}) {
            String half = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
            assertFalse(OlderLicenceFile.claims(ascii(half + ".AQID")), text);
        }
    }

    @Test
    void whatIsNoLicenceAtAllIsNot() {
        assertFalse(OlderLicenceFile.claims(null));
        assertFalse(OlderLicenceFile.claims(new byte[0]));
        assertFalse(OlderLicenceFile.claims(ascii(PAYLOAD_HALF)), "no dot: the text alone, cut short");
        assertFalse(OlderLicenceFile.claims(ascii("not base64!.AQID")));
        assertFalse(OlderLicenceFile.claims("PK\u0003\u0004 a zip, say".getBytes(StandardCharsets.ISO_8859_1)));
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] both = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        return both;
    }
}
