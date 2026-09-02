package com.hamza.account.features.shift;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiftAttributionMigrationTest {

    @Test
    void everyTreasuryMovementTableGetsAnExplicitShiftReference() {
        String migration = read("db/migration/V25__shift_cash_attribution.sql");
        for (String table : List.of("total_buy", "total_buy_re", "total_sales", "total_sales_re",
                "customers_accounts", "suppliers_accounts", "expenses_details",
                "treasury_deposit_expenses")) {
            assertTrue(migration.contains("ALTER TABLE " + table + " ADD COLUMN shift_id INT NULL"), table);
        }
        assertTrue(migration.contains("source_shift_id INT NULL"));
        assertTrue(migration.contains("destination_shift_id INT NULL"));
        assertTrue(migration.contains("ON DELETE RESTRICT"));
    }

    @Test
    void treasuryBalanceCarriesBothSidesOfTransferAttribution() {
        String views = read("db/migration/R__views.sql");
        assertTrue(views.contains("source_shift_id"));
        assertTrue(views.contains("destination_shift_id"));
        assertTrue(views.contains("c.shift_id"));
    }

    private static String read(String resource) {
        try (InputStream in = ShiftAttributionMigrationTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing resource: " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
