package com.hamza.account.features.productprofile;

import java.util.Set;

/** Metadata used by the setup utility and by dependency validation. */
public record ProductFeatureDefinition(
        FeatureKey key,
        String titleKey,
        String descriptionKey,
        String categoryKey,
        Set<FeatureKey> dependencies) {

    public ProductFeatureDefinition {
        dependencies = Set.copyOf(dependencies);
    }
}
