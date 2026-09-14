package com.hamza.account.features.invoice;

import com.hamza.account.controller.model.PrintPurchaseWithName;
import com.hamza.account.features.export.TreePdfLayout;
import com.hamza.account.model.domain.UnitsModel;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class MultiInvoicePdfLayoutTest {

    private static PrintPurchaseWithName line(int invoice, double quantity, double price, double discount,
                                              String item, String party, String date) {
        // total is quantity * price, before the discount - what InvoiceLineService stores.
        return new PrintPurchaseWithName(invoice, quantity, price, discount, quantity * price,
                new UnitsModel("قطعة"), item, party, date);
    }

    @Test
    void eachInvoiceIsABranchHoldingItsOwnLines() {
        var layout = MultiInvoicePdfLayout.of(List.of(
                line(12, 2, 10, 1, "قلم", "عميل أ", "2026-09-14"),
                line(12, 1, 5, 0, "كراسة", "عميل أ", "2026-09-14"),
                line(15, 3, 4, 0, "ممحاة", "عميل ب", "2026-09-15")), key -> key);

        List<TreePdfLayout.Branch> branches = layout.tree().branches();
        assertEquals(2, branches.size());
        assertEquals(2, layout.invoiceCount());
        assertTrue(branches.get(0).title().contains("12"));
        assertTrue(branches.get(0).title().contains("2026-09-14"));
        assertTrue(branches.get(0).title().contains("عميل أ"));
        assertEquals(2, branches.get(0).rows().size());
        assertEquals("قلم", branches.get(0).rows().get(0)[0]);
        assertEquals(1, branches.get(1).rows().size());
        assertTrue(branches.get(1).title().contains("عميل ب"));
    }

    @Test
    void theNetIsAfterTheDiscountBecauseTheStoredTotalIsBeforeIt() {
        var layout = MultiInvoicePdfLayout.of(List.of(
                line(12, 2, 10, 1, "قلم", "عميل", "2026-09-14"),
                line(12, 1, 5, 0, "كراسة", "عميل", "2026-09-14"),
                line(15, 3, 4, 0.5, "ممحاة", "عميل", "2026-09-15")), key -> key);

        TreePdfLayout.Branch first = layout.tree().branches().getFirst();
        String[] pen = first.rows().getFirst();
        assertEquals("20.00", pen[4], "before the discount");
        assertEquals("1.00", pen[5]);
        assertEquals("19.00", pen[6], "the net");

        assertEquals("25.00", first.summary()[4]);
        assertEquals("1.00", first.summary()[5]);
        assertEquals("24.00", first.summary()[6]);

        String[] totals = layout.tree().totals();
        assertEquals("37.00", totals[4]);
        assertEquals("1.50", totals[5]);
        assertEquals("35.50", totals[6]);
        assertEquals(0, layout.net().compareTo(new java.math.BigDecimal("35.50")));
    }

    /** Quantities in different units do not add up to anything, so no summary line carries one. */
    @Test
    void noSummaryLineAddsUpQuantities() {
        var layout = MultiInvoicePdfLayout.of(List.of(
                line(12, 2, 10, 0, "قلم", "عميل", "2026-09-14"),
                line(12, 7, 1, 0, "كراسة", "عميل", "2026-09-14")), key -> key);

        assertEquals("", layout.tree().branches().getFirst().summary()[2]);
        assertEquals("", layout.tree().totals()[2]);
    }

    @Test
    void anInvoiceSplitAcrossTheListIsStillOneBranch() {
        var layout = MultiInvoicePdfLayout.of(List.of(
                line(12, 1, 1, 0, "أ", "عميل", "2026-09-14"),
                line(15, 1, 1, 0, "ب", "عميل", "2026-09-14"),
                line(12, 1, 1, 0, "ج", "عميل", "2026-09-14")), key -> key);

        assertEquals(2, layout.tree().branches().size());
        assertEquals(2, layout.tree().branches().getFirst().rows().size());
    }

    /**
     * The keys reach LanguageManager through a method reference, which the message-key scan can
     * only see because the interface method is called {@code text}. Checked here as well, against
     * the bundles themselves, so renaming it cannot hide a missing key.
     */
    @Test
    void everyKeyTheLayoutAsksForExistsInEveryBundle() throws Exception {
        List<String> asked = new ArrayList<>();
        MultiInvoicePdfLayout.of(List.of(line(1, 1, 1, 0, "أ", "ب", "2026-09-14")),
                key -> {
                    asked.add(key);
                    return key;
                });
        assertFalse(asked.isEmpty());
        Path dir = Path.of("..", "controlsfx", "src", "main", "resources", "i18n");
        for (String bundle : new String[]{"messages.properties", "messages_ar.properties", "messages_en.properties"}) {
            Properties properties = new Properties();
            try (var reader = new InputStreamReader(Files.newInputStream(dir.resolve(bundle)), StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            for (String key : asked) {
                assertNotNull(properties.getProperty(key), key + " is missing from " + bundle);
            }
        }
    }
}
