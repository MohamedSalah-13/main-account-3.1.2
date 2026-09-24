package com.hamza.account.features.productprofile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** One declarative catalogue, shared by the runtime gate and the external setup screen. */
public final class ProductFeatureCatalog {

    private final List<ProductFeatureDefinition> definitions;
    private final Map<FeatureKey, ProductFeatureDefinition> byKey;

    public ProductFeatureCatalog(List<ProductFeatureDefinition> definitions) {
        this.definitions = List.copyOf(definitions);
        Map<FeatureKey, ProductFeatureDefinition> indexed = new LinkedHashMap<>();
        for (ProductFeatureDefinition definition : this.definitions) {
            if (indexed.put(definition.key(), definition) != null) {
                throw new IllegalArgumentException("Duplicate product feature: " + definition.key());
            }
        }
        for (ProductFeatureDefinition definition : this.definitions) {
            if (!indexed.keySet().containsAll(definition.dependencies())) {
                throw new IllegalArgumentException("Unknown dependency for " + definition.key());
            }
        }
        this.byKey = Map.copyOf(indexed);
    }

    public static ProductFeatureCatalog standard() {
        return new ProductFeatureCatalog(ProductFeatures.definitions());
    }

    public List<ProductFeatureDefinition> definitions() {
        return definitions;
    }

    public Set<FeatureKey> keys() {
        return byKey.keySet();
    }

    /**
     * Every feature but the add-ons: what a database with no profile and a version-1 profile turn on, and
     * what the "full" edition holds. An add-on is ticked by hand, for a shop that bought it.
     */
    public Set<FeatureKey> keysWithoutAddOns() {
        return definitions.stream().filter(definition -> !definition.addOn())
                .map(ProductFeatureDefinition::key)
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAddOn(FeatureKey key) {
        ProductFeatureDefinition definition = byKey.get(key);
        return definition != null && definition.addOn();
    }

    public boolean contains(FeatureKey key) {
        return byKey.containsKey(key);
    }

    public void validateSelection(Set<FeatureKey> enabled) throws ProductProfileException {
        for (FeatureKey key : enabled) {
            ProductFeatureDefinition definition = byKey.get(key);
            if (definition == null) {
                throw new ProductProfileException("product.profile.error.unknown.feature", key.value());
            }
            if (!enabled.containsAll(definition.dependencies())) {
                throw new ProductProfileException("product.profile.error.missing.dependency", key.value());
            }
        }
    }
}
