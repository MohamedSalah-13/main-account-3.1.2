package com.hamza.account.features.pricing;

import com.hamza.account.view.barcode.PrintBarcodeModel;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceTierFormTest {

    @Test
    @DisplayName("a tier is filled from nothing, the cost, or another tier - never itself")
    void choices() {
        List<PriceTierForm.RuleSource> choices = PriceTierForm.RuleSource.choicesFor(2);
        assertEquals(List.of(PriceTierForm.RuleSource.NONE, PriceTierForm.RuleSource.COST,
                PriceTierForm.RuleSource.tier(1), PriceTierForm.RuleSource.tier(3)), choices);
        assertEquals(PriceTierForm.RuleSource.tier(1),
                PriceTierForm.RuleSource.of(TierFillRule.fromTier(1, BigDecimal.ONE, BigDecimal.ONE)));
        assertTrue(PriceTierForm.RuleSource.of(null).none());
    }

    @Test
    @DisplayName("a rule needs its percentage and its rounding, each refused with its own key")
    void rule() throws Exception {
        assertNull(PriceTierForm.rule(PriceTierForm.RuleSource.NONE, null, null));
        assertEquals(TierFillRule.fromCost(new BigDecimal("12"), new BigDecimal("0.05")),
                PriceTierForm.rule(PriceTierForm.RuleSource.COST, new BigDecimal("12"), new BigDecimal("0.05")));
        assertRefused("pricing.tier.error.rule.percent.required", PriceTierForm.RuleSource.COST, null, BigDecimal.ONE);
        assertRefused("pricing.tier.error.rule.percent", PriceTierForm.RuleSource.COST, new BigDecimal("-100"),
                BigDecimal.ONE);
        assertRefused("pricing.tier.error.rule.rounding", PriceTierForm.RuleSource.COST, BigDecimal.ONE, null);
    }

    @Test
    @DisplayName("switching off a tier customers are on is named, with how many; one nobody is on is not")
    void switchedOff() {
        PriceTierCatalog before = new PriceTierCatalog(List.of(new PriceTier(1, "قطاعي", true, null),
                new PriceTier(2, "تجزئة", true, null), new PriceTier(3, "جملة", true, null)));
        List<PriceTier> after = List.of(new PriceTier(1, "قطاعي", true, null),
                new PriceTier(2, "تجزئة", false, null), new PriceTier(3, "جملة", false, null));
        Map<PriceTier, Integer> affected = PriceTierForm.switchedOffUnderCustomers(before, after, Map.of(3, 12));
        assertEquals(Map.of(after.get(2), 12), affected);
    }

    @Test
    @DisplayName("a label prints the chosen tier's price, and tier 1's where that tier has none")
    void labelPrice() {
        ItemsModel item = new ItemsModel(5, "B5", "صابون");
        item.setSelPrice1(10);
        item.setSelPrice2(9.5);
        PrintBarcodeModel label = PrintBarcodeModel.of(item);
        assertEquals(new BigDecimal("10.00"), label.getPrice());
        label.showTier(2);
        assertEquals(new BigDecimal("9.50"), label.getPrice());
        label.showTier(3);
        assertEquals(new BigDecimal("10.00"), label.getPrice());
    }

    private static void assertRefused(String key, PriceTierForm.RuleSource source, BigDecimal percent,
                                      BigDecimal rounding) {
        UserValidationException refused = assertThrows(UserValidationException.class,
                () -> PriceTierForm.rule(source, percent, rounding));
        assertEquals(key, refused.getMessage());
    }
}
