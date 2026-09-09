package com.hamza.account.features.productprofile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductFeaturesTest {

    @Test
    void catalogueContainsAllFunctionalSidebarScreens() {
        assertEquals(51, ProductFeatures.definitions().size());
        assertEquals(51, ProductFeatures.allKeys().size());
        assertTrue(ProductFeatures.keysInCategory(ProductFeatures.CATEGORY_SALES)
                .contains(ProductFeatures.SALES_CREATE));
        assertTrue(ProductFeatures.keysInCategory(ProductFeatures.CATEGORY_SYSTEM)
                .contains(ProductFeatures.SYSTEM_BACKUP));
    }
}
