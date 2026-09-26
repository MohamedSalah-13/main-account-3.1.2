package com.hamza.account.features.license.online;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The code and its check are contract with the licence server ({@code server-plan.md} §4.2): the fixed
 * example below is the one its own {@code PurchaseCodeTest} and {@code contract/README.md} pin, so a change
 * on either side breaks the other side's test.
 */
class PurchaseCodeTest {

    /** {@code 0123456789ABCDE} checks to {@code Z}: the server's fixed example. */
    private static final String CANONICAL = "AK0123456789ABCDEZ";

    @Test
    void theServersFixedExampleChecksToZ() {
        assertEquals('Z', PurchaseCode.checkCharacter("0123456789ABCDE"));
        assertEquals(Optional.of(CANONICAL), PurchaseCode.normalise("AK-0123-4567-89AB-CDEZ"));
    }

    @Test
    void itIsReadTheWayAPersonTypesIt() {
        for (String typed : new String[]{"AK-0123-4567-89AB-CDEZ", "ak-0123-4567-89ab-cdez", "AK 0123 4567 89AB CDEZ",
                "0123-4567-89AB-CDEZ", "  0123456789abcdez  ", "AK-O123-4567-89AB-CDEZ", "ak0123456789abcdez"}) {
            assertEquals(Optional.of(CANONICAL), PurchaseCode.normalise(typed), typed);
        }
        // I and L are read as 1: a code whose random part has a 1 is typed either way.
        String withOne = "1" + "23456789ABCDEF";
        String code = withOne + PurchaseCode.checkCharacter(withOne);
        String expected = "AK" + code;
        assertEquals(Optional.of(expected), PurchaseCode.normalise("AK-" + code.replace('1', 'I')));
        assertEquals(Optional.of(expected), PurchaseCode.normalise("AK-" + code.replace('1', 'l')));
    }

    @Test
    void whatCannotBeTheCodeIsRefusedBeforeAnythingIsSent() {
        for (String typed : new String[]{null, "", "   ", "AK-0123-4567-89AB-CDE", "AK-0123-4567-89AB-CDEZZ",
                "AK-0123-4567-89AB-CDEY", "SC-0123-4567-89AB-CDEZ", "AK-0123-4567-89AB-CDUZ", "AK-0123-4567-89AB-CD#Z"}) {
            assertTrue(PurchaseCode.normalise(typed).isEmpty(), String.valueOf(typed));
        }
    }

    /** What the check is for: one character wrong, anywhere, is caught - and is caught here, not by the server. */
    @Test
    void everySingleWrongCharacterIsCaught() {
        Random random = new Random(42);
        for (int round = 0; round < 200; round++) {
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < PurchaseCode.RANDOM_CHARS; i++) {
                body.append(PurchaseCode.ALPHABET.charAt(random.nextInt(PurchaseCode.ALPHABET.length())));
            }
            String code = body.toString() + PurchaseCode.checkCharacter(body);
            assertTrue(PurchaseCode.normalise(code).isPresent(), code);
            for (int at = 0; at < code.length(); at++) {
                for (char other : PurchaseCode.ALPHABET.toCharArray()) {
                    if (other == code.charAt(at)) {
                        continue;
                    }
                    String wrong = code.substring(0, at) + other + code.substring(at + 1);
                    assertTrue(PurchaseCode.normalise(wrong).isEmpty(), wrong + " passed for " + code);
                }
            }
        }
    }
}
