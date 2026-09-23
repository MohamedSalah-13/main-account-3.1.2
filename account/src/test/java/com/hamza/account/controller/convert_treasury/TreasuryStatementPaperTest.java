package com.hamza.account.controller.convert_treasury;

import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.treasury.statement.TreasuryMovementKind;
import com.hamza.account.features.treasury.statement.TreasuryStatementCurrency;
import com.hamza.account.features.treasury.statement.TreasuryStatementFilter;
import com.hamza.account.features.treasury.statement.TreasuryStatementPrintData;
import com.hamza.account.features.treasury.statement.TreasuryStatementRow;
import com.hamza.account.features.treasury.statement.TreasuryStatementSummary;
import com.hamza.account.features.treasury.statement.TreasuryStatementTotals;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The printed treasury statement. A Kuwaiti dinar drawer is the foreign case on purpose: its three
 * places are what {@code Columns.money}'s two would silently round away.
 */
class TreasuryStatementPaperTest {

    private static final Currency DINAR = new Currency(3, "KWD", "دينار كويتي", "د.ك", "KD", 3, false, true, 3);

    /** 1.250 dinars in, worth 196.25 in the books, the drawer then holding 3.750 at a book value of 588.75. */
    private static final TreasuryStatementRow DEPOSIT = new TreasuryStatementRow(
            41, LocalDate.of(2026, 9, 21), null, TreasuryMovementKind.DEPOSIT, "إيداع", 5, "درج الدينار",
            new BigDecimal("196.25"), BigDecimal.ZERO, new BigDecimal("588.75"),
            new BigDecimal("1.250"), BigDecimal.ZERO, new BigDecimal("3.750"), 9, "operator");

    private static final TreasuryStatementTotals TOTALS = new TreasuryStatementTotals(
            new TreasuryStatementSummary(new BigDecimal("392.50"), new BigDecimal("196.25"),
                    BigDecimal.ZERO, new BigDecimal("588.75")),
            new TreasuryStatementSummary(new BigDecimal("2.500"), new BigDecimal("1.250"),
                    BigDecimal.ZERO, new BigDecimal("3.750")));

    private static final TreasuryStatementFilter PERIOD = new TreasuryStatementFilter(
            LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 23), 5, null, null, 0, 250);

    @Test
    @DisplayName("a foreign treasury's paper is in its currency, to its places, with the books' value beside it")
    void aForeignPaper() {
        TreasuryStatementPrintData data = new TreasuryStatementPrintData(List.of(DEPOSIT), TOTALS, false,
                new TreasuryStatementCurrency(DINAR));

        TablePdfLayout layout = TreasuryStatementPaper.layout(data);

        assertEquals(10, layout.headers().length);
        assertEquals(text("treasury.statement.column.income") + " (KWD)", layout.headers()[5]);
        assertEquals(text("treasury.statement.column.balance") + " (KWD)", layout.headers()[7]);
        assertEquals(text("treasury.statement.column.book"), layout.headers()[8]);
        assertEquals(layout.headers().length, layout.columnWidths().length);
        String[] row = layout.rows().get(0);
        assertEquals("1.250", row[5], "three places for a dinar, not the books' two");
        assertEquals("3.750", row[7]);
        assertEquals(Columns.money(new BigDecimal("196.25")), row[8]);
        assertEquals("operator", row[9]);
        assertArrayEquals(new String[]{text("total"), "", "", "", "", "1.250", "0.000", "3.750",
                Columns.money(new BigDecimal("196.25")), ""}, layout.totals());
    }

    @Test
    @DisplayName("a foreign paper's subtitle carries the opening in its currency and the closing book value")
    void aForeignSubtitle() {
        TreasuryStatementPrintData data = new TreasuryStatementPrintData(List.of(DEPOSIT), TOTALS, false,
                new TreasuryStatementCurrency(DINAR));

        String subtitle = TreasuryStatementPaper.subtitle(PERIOD, "درج الدينار (KWD)", "", data);

        assertTrue(subtitle.contains(text("treasury.statement.print.opening") + ": 2.500"), subtitle);
        assertTrue(subtitle.contains(LanguageManager.getInstance().getString("treasury.statement.currency.note",
                "KWD", Columns.money(new BigDecimal("588.75")))), subtitle);
    }

    @Test
    @DisplayName("a statement in the base prints as it always did: the books' figures, nine columns, no note")
    void aBasePaper() {
        TreasuryStatementPrintData data = new TreasuryStatementPrintData(List.of(DEPOSIT), TOTALS, false,
                TreasuryStatementCurrency.BASE);

        TablePdfLayout layout = TreasuryStatementPaper.layout(data);
        String subtitle = TreasuryStatementPaper.subtitle(PERIOD, "all", "", data);

        assertEquals(9, layout.headers().length);
        assertEquals(text("treasury.statement.column.income"), layout.headers()[5]);
        assertEquals(Columns.money(new BigDecimal("196.25")), layout.rows().get(0)[5]);
        assertEquals(Columns.money(new BigDecimal("588.75")), layout.rows().get(0)[7]);
        assertEquals(Columns.money(new BigDecimal("588.75")), layout.totals()[7]);
        assertTrue(subtitle.endsWith(Columns.money(new BigDecimal("392.50"))), subtitle);
        assertFalse(subtitle.contains("KWD"));
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
