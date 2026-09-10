package com.hamza.account.features.productprofile;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Set;

/** Builds a localized, plain-text delivery record without depending on JavaFX. */
public final class ProductProfileSummaryFormatter {

    private ProductProfileSummaryFormatter() {
    }

    public static String format(String customerName, String profileName, Instant generatedAt,
                                Set<FeatureKey> enabled, ProductFeatureCatalog catalog,
                                TextResolver text, Locale locale, ZoneId zone) {
        DateTimeFormatter dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(locale)
                .withZone(zone);
        StringBuilder summary = new StringBuilder();
        summary.append(text.get("product.profile.summary.title")).append(System.lineSeparator());
        summary.append(text.get("product.profile.summary.customer", customerName.strip()))
                .append(System.lineSeparator());
        summary.append(text.get("product.profile.summary.edition", profileName.strip()))
                .append(System.lineSeparator());
        summary.append(text.get("product.profile.summary.generated", dateTime.format(generatedAt)))
                .append(System.lineSeparator());
        summary.append(text.get("product.profile.summary.count", enabled.size(), catalog.keys().size()))
                .append(System.lineSeparator());

        String currentCategory = null;
        for (ProductFeatureDefinition definition : catalog.definitions()) {
            if (!enabled.contains(definition.key())) continue;
            if (!definition.categoryKey().equals(currentCategory)) {
                currentCategory = definition.categoryKey();
                summary.append(System.lineSeparator())
                        .append(text.get(currentCategory)).append(':').append(System.lineSeparator());
            }
            summary.append("- ").append(text.get(definition.titleKey())).append(System.lineSeparator());
        }
        return summary.toString();
    }

    @FunctionalInterface
    public interface TextResolver {
        String get(String key, Object... arguments);
    }
}
