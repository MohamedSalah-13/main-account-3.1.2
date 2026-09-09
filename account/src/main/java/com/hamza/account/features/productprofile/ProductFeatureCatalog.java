package com.hamza.account.features.productprofile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
