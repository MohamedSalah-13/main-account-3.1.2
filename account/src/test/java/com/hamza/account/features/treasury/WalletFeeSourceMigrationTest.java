package com.hamza.account.features.treasury;

import com.hamza.account.features.shift.ShiftCashSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V67's CHECK and {@link WalletFeeSource} say one thing twice - which kinds of movement can carry
 * a fee - so this reads the range out of the migration and holds the record to it. A kind the
 * record accepts and the CHECK refuses is a save that fails on a customer's database only.
 */
class WalletFeeSourceMigrationTest {

    private static final String MIGRATION = "/db/migration/V67__wallet_fee_source.sql";

    @Test
    @DisplayName("the CHECK's range of kinds is exactly what WalletFeeSource accepts")
    void theCheckAndTheRecordAgree() throws IOException {
        Matcher range = Pattern.compile("fee_source_type BETWEEN (\\d+) AND (\\d+)").matcher(migration());
        assertTrue(range.find(), "V67 no longer states the range of fee_source_type");
        int lowest = Integer.parseInt(range.group(1));
        int highest = Integer.parseInt(range.group(2));

        for (ShiftCashSource kind : ShiftCashSource.values()) {
            boolean allowedBySql = kind.code() >= lowest && kind.code() <= highest;
            if (allowedBySql) {
                assertDoesNotThrow(() -> new WalletFeeSource(kind, 1), kind.name());
            } else {
                assertThrows(IllegalArgumentException.class, () -> new WalletFeeSource(kind, 1), kind.name());
            }
        }
    }

    @Test
    @DisplayName("one fee per movement, and the link is both columns or neither")
    void theLinkIsUniqueAndWhole() throws IOException {
        String sql = migration();
        assertTrue(sql.contains("'UNIQUE (fee_source_type, fee_source_id)'"));
        assertTrue(sql.contains("fee_source_type IS NULL AND fee_source_id IS NULL"));
    }

    @Test
    @DisplayName("the kinds keep the numbers the stored rows were written with")
    void theCodesAreStable() {
        assertEquals(1, ShiftCashSource.PURCHASE.code());
        assertEquals(4, ShiftCashSource.SALES_RETURN.code());
        assertEquals(5, ShiftCashSource.CUSTOMER_ACCOUNT.code());
        assertEquals(6, ShiftCashSource.SUPPLIER_ACCOUNT.code());
    }

    private static String migration() throws IOException {
        try (InputStream in = WalletFeeSourceMigrationTest.class.getResourceAsStream(MIGRATION)) {
            assertTrue(in != null, MIGRATION + " is not on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
