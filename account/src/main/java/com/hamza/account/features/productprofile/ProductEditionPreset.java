package com.hamza.account.features.productprofile;

import java.util.Set;

/** Editable starting point for issuing a client edition; it is not persisted as policy. */
public record ProductEditionPreset(
        String id,
        String nameKey,
        String descriptionKey,
        Set<FeatureKey> enabledFeatures) {

    public ProductEditionPreset {
        enabledFeatures = Set.copyOf(enabledFeatures);
    }
}
