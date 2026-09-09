package com.hamza.account.features.productprofile;

import java.time.Instant;
import java.util.Set;

/** Values authored by the technician before they are signed. */
public record ProductProfileDraft(
        String customerName,
        String profileName,
        Set<FeatureKey> enabledFeatures,
        Instant issuedAt) {

    public ProductProfileDraft {
        customerName = customerName == null ? "" : customerName.strip();
        profileName = profileName == null ? "" : profileName.strip();
        enabledFeatures = Set.copyOf(enabledFeatures);
        issuedAt = issuedAt == null ? Instant.now() : issuedAt;
    }
}
