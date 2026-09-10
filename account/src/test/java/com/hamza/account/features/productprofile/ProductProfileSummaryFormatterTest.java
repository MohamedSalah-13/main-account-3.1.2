package com.hamza.account.features.productprofile;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductProfileSummaryFormatterTest {

    @Test
    void listsOnlyEnabledScreensInCatalogueOrder() {
        ProductFeatureCatalog catalog = ProductFeatureCatalog.standard();

        String summary = ProductProfileSummaryFormatter.format(
                "Test Shop", "Sales", Instant.parse("2026-09-10T08:30:00Z"),
                Set.of(ProductFeatures.SALES_CREATE, ProductFeatures.ITEMS_PRICE_CHECK),
                catalog, (key, arguments) -> key + formatArguments(arguments),
                Locale.ENGLISH, ZoneId.of("UTC"));

        assertTrue(summary.contains("product.profile.summary.customer[Test Shop]"));
        assertTrue(summary.contains("product.profile.feature.sales.create"));
        assertTrue(summary.contains("product.profile.feature.items.price.check"));
        assertFalse(summary.contains("product.profile.feature.items.merge"));
    }

    private static String formatArguments(Object[] arguments) {
        return arguments.length == 0 ? "" : "[" + String.join(",", java.util.Arrays.stream(arguments)
                .map(String::valueOf).toList()) + "]";
    }
}
