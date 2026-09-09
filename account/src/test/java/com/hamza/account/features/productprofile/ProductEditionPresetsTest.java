package com.hamza.account.features.productprofile;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductEditionPresetsTest {

    @Test
    void standardPresetsHaveUniqueIdsAndOnlyKnownFeatures() {
        ProductFeatureCatalog catalog = ProductFeatureCatalog.standard();
        List<ProductEditionPreset> presets = ProductEditionPresets.standard(catalog);

        assertEquals(presets.size(), new HashSet<>(presets.stream().map(ProductEditionPreset::id).toList()).size());
        presets.forEach(preset -> assertTrue(catalog.keys().containsAll(preset.enabledFeatures())));
    }

    @Test
    void fullContainsEveryScreenAndFocusedEditionsRemainDifferent() {
        ProductFeatureCatalog catalog = ProductFeatureCatalog.standard();
        List<ProductEditionPreset> presets = ProductEditionPresets.standard(catalog);

        ProductEditionPreset full = presets.stream().filter(preset -> preset.id().equals("full")).findFirst().orElseThrow();
        ProductEditionPreset sales = presets.stream().filter(preset -> preset.id().equals("sales")).findFirst().orElseThrow();
        ProductEditionPreset inventory = presets.stream().filter(preset -> preset.id().equals("inventory")).findFirst().orElseThrow();

        assertEquals(catalog.keys(), full.enabledFeatures());
        assertTrue(sales.enabledFeatures().contains(ProductFeatures.SALES_CREATE));
        assertFalse(sales.enabledFeatures().contains(ProductFeatures.PURCHASES_CREATE));
        assertTrue(inventory.enabledFeatures().contains(ProductFeatures.ITEMS_STOCK_COUNT));
        assertFalse(inventory.enabledFeatures().contains(ProductFeatures.SALES_CREATE));
    }
}
