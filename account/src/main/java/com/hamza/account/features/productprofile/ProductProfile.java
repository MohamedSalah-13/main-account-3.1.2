package com.hamza.account.features.productprofile;

import java.time.Instant;
import java.util.Set;

/** Immutable, verified product edition loaded once during application bootstrap. */
public record ProductProfile(
        int schemaVersion,
        String customerName,
        String profileName,
        Instant issuedAt,
        Set<FeatureKey> enabledFeatures,
        boolean legacyFallback) implements ProductFeatureAccess {

    public ProductProfile {
        enabledFeatures = Set.copyOf(enabledFeatures);
    }

    public static ProductProfile legacyFull(ProductFeatureCatalog catalog) {
        return new ProductProfile(1, "", "LEGACY_FULL", Instant.EPOCH, catalog.keys(), true);
    }

    @Override
    public boolean isEnabled(FeatureKey feature) {
        return enabledFeatures.contains(feature);
    }
}
