package com.hamza.account.features.productprofile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductFeaturesTest {

    @Test
    void catalogueContainsAllFunctionalSidebarScreens() {
        // 50 since reports.details went: a button that was permanently disabled and opened nothing - and
        // the offers add-on (V85) makes 51.
        assertEquals(51, ProductFeatures.definitions().size());
        assertEquals(51, ProductFeatures.allKeys().size());
        assertTrue(ProductFeatures.keysInCategory(ProductFeatures.CATEGORY_SALES)
                .contains(ProductFeatures.SALES_CREATE));
        assertTrue(ProductFeatures.keysInCategory(ProductFeatures.CATEGORY_SYSTEM)
                .contains(ProductFeatures.SYSTEM_BACKUP));
    }

    /**
     * The list of add-ons, in both directions: a feature marked an add-on by mistake disappears from every
     * install without a profile, and one left unmarked is given away to all of them.
     */
    @Test
    @DisplayName("the add-ons are exactly the offers, in a category of their own")
    void addOns() {
        assertEquals(Set.of(ProductFeatures.OFFERS), ProductFeatures.addOns());
        assertEquals(Set.of(ProductFeatures.OFFERS), ProductFeatures.keysInCategory(ProductFeatures.CATEGORY_ADD_ONS));
        ProductFeatureCatalog catalog = ProductFeatureCatalog.standard();
        assertTrue(catalog.isAddOn(ProductFeatures.OFFERS));
        assertFalse(catalog.isAddOn(ProductFeatures.ITEMS_LIST));
        assertFalse(catalog.keysWithoutAddOns().contains(ProductFeatures.OFFERS));
        assertEquals(catalog.keys().size() - 1, catalog.keysWithoutAddOns().size());
    }

    @Test
    @DisplayName("a database with no profile keeps every screen it had and gains no add-on")
    void legacyFullHasNoAddOn() {
        ProductProfile legacy = ProductProfile.legacyFull(ProductFeatureCatalog.standard());
        assertTrue(legacy.isEnabled(ProductFeatures.SALES_CREATE));
        assertTrue(legacy.isEnabled(ProductFeatures.ITEMS_PRICE_CHECK));
        assertFalse(legacy.isEnabled(ProductFeatures.OFFERS));
    }
}
