package com.hamza.account.features.productprofile;

import java.util.List;
import java.util.Set;

/** The optional product capabilities currently configurable per client database. */
public final class ProductFeatures {

    public static final FeatureKey ITEMS_MERGE = FeatureKey.of("items.merge");
    public static final FeatureKey ITEMS_PRICE_CHECK = FeatureKey.of("items.price-check");

    private static final List<ProductFeatureDefinition> DEFINITIONS = List.of(
            new ProductFeatureDefinition(
                    ITEMS_MERGE,
                    "product.profile.feature.items.merge",
                    "product.profile.feature.items.merge.description",
                    "product.profile.category.items",
                    Set.of()),
            new ProductFeatureDefinition(
                    ITEMS_PRICE_CHECK,
                    "product.profile.feature.items.price.check",
                    "product.profile.feature.items.price.check.description",
                    "product.profile.category.items",
                    Set.of()));

    private ProductFeatures() {
    }

    public static List<ProductFeatureDefinition> definitions() {
        return DEFINITIONS;
    }

    public static Set<FeatureKey> allKeys() {
        return Set.of(ITEMS_MERGE, ITEMS_PRICE_CHECK);
    }
}
