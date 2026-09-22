package com.hamza.account.features.productprofile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductFeaturesTest {

    @Test
    void catalogueContainsAllFunctionalSidebarScreens() {
        // 50 since reports.details went: a button that was permanently disabled and opened nothing.
        assertEquals(50, ProductFeatures.definitions().size());
        assertEquals(50, ProductFeatures.allKeys().size());
        assertTrue(ProductFeatures.keysInCategory(ProductFeatures.CATEGORY_SALES)
                .contains(ProductFeatures.SALES_CREATE));
        assertTrue(ProductFeatures.keysInCategory(ProductFeatures.CATEGORY_SYSTEM)
                .contains(ProductFeatures.SYSTEM_BACKUP));
    }
}
