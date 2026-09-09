package com.hamza.account.features.productprofile;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductFeatureCatalogTest {

    @Test
    void everyShippedFeatureHasOneDefinition() {
        ProductFeatureCatalog catalog = ProductFeatureCatalog.standard();

        assertEquals(ProductFeatures.allKeys(), catalog.keys());
        assertEquals(ProductFeatures.allKeys().size(), catalog.definitions().size());
    }

    @Test
    void refusesADefinitionDependingOnAnUnknownFeature() {
        ProductFeatureDefinition invalid = new ProductFeatureDefinition(
                FeatureKey.of("test.feature"), "title", "description", "category",
                Set.of(FeatureKey.of("missing.feature")));

        assertThrows(IllegalArgumentException.class,
                () -> new ProductFeatureCatalog(List.of(invalid)));
    }
}
