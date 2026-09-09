package com.hamza.account.features.productprofile;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable identifier for a capability sold as part of a client product profile. */
public record FeatureKey(String value) implements Comparable<FeatureKey> {

    private static final Pattern VALID = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+");

    public FeatureKey {
        value = Objects.requireNonNull(value, "feature key").trim();
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid product feature key: " + value);
        }
    }

    public static FeatureKey of(String value) {
        return new FeatureKey(value);
    }

    @Override
    public int compareTo(FeatureKey other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
