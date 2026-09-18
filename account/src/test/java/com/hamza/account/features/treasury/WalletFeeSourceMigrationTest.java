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
    /** V68 replaced the CHECK of V67 so a transfer can carry a fee; the newest statement of the rule is the rule. */
    private static final String CURRENT_CHECK = "/db/migration/V68__transfer_fee_source.sql";

    @Test
    @DisplayName("the CHECK's range of kinds is exactly what WalletFeeSource accepts")
    void theCheckAndTheRecordAgree() throws IOException {
        Matcher list = Pattern.compile("fee_source_type IN \\(([0-9, ]+)\\)").matcher(read(CURRENT_CHECK));
        assertTrue(list.find(), "V68 no longer lists the kinds fee_source_type may hold");
        java.util.Set<Integer> allowed = new java.util.HashSet<>();
        for (String code : list.group(1).split(",")) {
            allowed.add(Integer.parseInt(code.trim()));
        }

        for (ShiftCashSource kind : ShiftCashSource.values()) {
            boolean allowedBySql = allowed.contains(kind.code());
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
        assertEquals(11, ShiftCashSource.TRANSFER_OUT.code());
    }

    private static String migration() throws IOException {
        return read(MIGRATION);
    }

    private static String read(String resource) throws IOException {
        try (InputStream in = WalletFeeSourceMigrationTest.class.getResourceAsStream(resource)) {
            assertTrue(in != null, resource + " is not on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
