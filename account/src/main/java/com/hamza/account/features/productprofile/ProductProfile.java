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

    /**
     * A database with no profile keeps every screen it had - and gains no add-on, which was never a screen
     * it had (docs/pricing-and-offers-plan.md §6.1).
     */
    public static ProductProfile legacyFull(ProductFeatureCatalog catalog) {
        return new ProductProfile(ProductProfileCodec.SCHEMA_VERSION, "", "LEGACY_FULL",
                Instant.EPOCH, catalog.keysWithoutAddOns(), true);
    }

    @Override
    public boolean isEnabled(FeatureKey feature) {
        return enabledFeatures.contains(feature);
    }
}
