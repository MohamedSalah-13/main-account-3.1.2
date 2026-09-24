package com.hamza.account.features.productprofile;

import java.util.Set;

/**
 * Metadata used by the setup utility and by dependency validation.
 * <p>
 * {@code addOn} marks a feature sold on its own (docs/pricing-and-offers-plan.md §6.1). Every other feature
 * reaches a profile that does not name it by two roads - a database with no profile ({@code LEGACY_FULL})
 * and a version-1 profile, which both turn on everything - so that an update never takes a screen away. An
 * add-on is the exception: it was never a screen anybody had, and those two roads would give it away.
 */
public record ProductFeatureDefinition(
        FeatureKey key,
        String titleKey,
        String descriptionKey,
        String categoryKey,
        Set<FeatureKey> dependencies,
        boolean addOn) {

    public ProductFeatureDefinition {
        dependencies = Set.copyOf(dependencies);
    }

    public ProductFeatureDefinition(FeatureKey key, String titleKey, String descriptionKey, String categoryKey,
                                    Set<FeatureKey> dependencies) {
        this(key, titleKey, descriptionKey, categoryKey, dependencies, false);
    }
}
