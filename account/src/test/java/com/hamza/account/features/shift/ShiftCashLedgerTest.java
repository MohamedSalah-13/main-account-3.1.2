package com.hamza.account.features.shift;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.treasury.MovementLabel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class ShiftCashLedgerTest {

    @Test
    void everySourceHasAStableDistinctCodeAndHeading() {
        assertEquals(ShiftCashSource.values().length,
                new HashSet<>(Arrays.stream(ShiftCashSource.values()).map(ShiftCashSource::code).toList()).size());
        for (ShiftCashSource source : ShiftCashSource.values()) {
            assertTrue(source.code() > 0);
            assertNotNull(source.label());
        }
    }

    @Test
    void documentAndPartyCashDirectionsMatchTheTreasuryView() {
        assertEquals(MovementLabel.SALES, ShiftCashSource.document(DocumentType.SALES).label());
        assertEquals(MovementLabel.SALES_RETURNS, ShiftCashSource.document(DocumentType.SALES_RETURN).label());
        assertEquals(MovementLabel.PURCHASES, ShiftCashSource.document(DocumentType.PURCHASE).label());
        assertEquals(MovementLabel.PURCHASE_RETURNS, ShiftCashSource.document(DocumentType.PURCHASE_RETURN).label());
        assertEquals(ShiftCashSource.CUSTOMER_ACCOUNT, ShiftCashSource.party(PartyKind.CUSTOMER));
        assertEquals(ShiftCashSource.SUPPLIER_ACCOUNT, ShiftCashSource.party(PartyKind.SUPPLIER));
    }

    @Test
    void effectsKeepIncomeAndOutputOnSeparateSides() {
        var incoming = ShiftCashEffect.incoming(ShiftCashSource.SALES, 8, 2, 3, new BigDecimal("12.50"));
        var outgoing = ShiftCashEffect.outgoing(ShiftCashSource.PURCHASE, 9, 2, 3, new BigDecimal("7.25"));
        assertEquals(new BigDecimal("12.50"), incoming.income());
        assertEquals(BigDecimal.ZERO, incoming.output());
        assertEquals(BigDecimal.ZERO, outgoing.income());
        assertEquals(new BigDecimal("7.25"), outgoing.output());
    }

    @Test
    void migrationEnforcesAppendOnlyAndIndexesSourceIdentity() {
        String sql = read("db/migration/V26__shift_cash_ledger.sql");
        assertTrue(sql.contains("CREATE TABLE shift_cash_ledger"));
        assertTrue(sql.contains("income_delta   DECIMAL(19, 4)"));
        assertTrue(sql.contains("output_delta   DECIMAL(19, 4)"));
        assertTrue(sql.contains("prevent_shift_cash_ledger_update"));
        assertTrue(sql.contains("prevent_shift_cash_ledger_delete"));
        assertTrue(sql.contains("@app_bulk_wipe"));
        assertTrue(sql.contains("idx_shift_cash_ledger_source"));
        assertTrue(sql.contains("chk_shift_cash_ledger_correction_reason"));
        assertTrue(sql.contains("TRIM(COALESCE(reason, ''))"));
    }

    @Test
    void reconciliationMigrationPreservesOriginAndMakesCloseSnapshotsImmutable() {
        String sql = read("db/migration/V27__shift_close_snapshot_and_reconciliation.sql");
        assertTrue(sql.contains("ADD COLUMN origin_shift_id"));
        assertTrue(sql.contains("CREATE TABLE shift_close_snapshots"));
        assertTrue(sql.contains("ledger_last_id"));
        assertTrue(sql.contains("ledger_complete"));
        assertTrue(sql.contains("prevent_shift_close_snapshot_update"));
        assertTrue(sql.contains("prevent_shift_close_snapshot_delete"));
        assertTrue(sql.contains("@app_bulk_wipe"));
    }

    private static String read(String resource) {
        try (InputStream in = ShiftCashLedgerTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
