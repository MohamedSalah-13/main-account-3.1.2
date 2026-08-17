package com.hamza.account.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that every theme carries the same visual vocabulary for the four documents -
 * not just {@code .invoice-return}. Before this, {@code .invoice-sales},
 * {@code .invoice-purchases} and {@code .invoice-return} existed only in
 * {@code theme-light.css}: switching to dark or glass mode did not just lose the
 * return's colour, it made a sale, a purchase and a return look identical, because
 * none of the three had any colour at all outside the light theme.
 * <p>
 * No JavaFX toolkit and no rendering - this only checks that the class selectors and
 * the variables they read are present as text, the same guarantee
 * {@code WipeCatalogTest} gives the wipe catalog against the schema. It cannot check
 * that the chosen colours look right; that is still something to open the app and see.
 */
class InvoiceThemeColorsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "com/hamza/account/css/theme-light.css",
            "com/hamza/account/css/theme-dark.css",
            "com/hamza/account/css/glass-theme.css",
    })
    @DisplayName("every theme defines all three invoice-type variable blocks")
    void definesAllThreeInvoiceTypes(String resource) {
        String css = read(resource);
        assertTrue(css.contains(".invoice-sales {"), resource + " is missing .invoice-sales");
        assertTrue(css.contains(".invoice-purchases {"), resource + " is missing .invoice-purchases");
        assertTrue(css.contains(".invoice-return {"), resource + " is missing .invoice-return");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "com/hamza/account/css/theme-light.css",
            "com/hamza/account/css/theme-dark.css",
            "com/hamza/account/css/glass-theme.css",
    })
    @DisplayName("every theme's return class is styled, not left to fall through to purchases")
    void theReturnClassCarriesItsOwnRowAndSummaryStyling(String resource) {
        String css = read(resource);
        assertTrue(css.contains(".invoice-return .table-row-cell:even"),
                resource + " has no return row styling");
        assertTrue(css.contains(".invoice-return .summary-card"),
                resource + " has no return summary-card styling");
    }

    @Test
    @DisplayName("the six invoice-type variables are defined and used consistently")
    void definesTheSixVariablesEachClassNeeds() {
        for (String resource : new String[]{
                "com/hamza/account/css/theme-light.css",
                "com/hamza/account/css/theme-dark.css",
                "com/hamza/account/css/glass-theme.css"}) {
            String css = read(resource);
            for (String variable : new String[]{"-invoice-main", "-invoice-main-dark",
                    "-invoice-soft", "-invoice-row-hover", "-invoice-selected", "-invoice-text"}) {
                assertTrue(css.contains(variable + ":"), resource + " never defines " + variable);
                assertTrue(css.contains("-fx-background-color: " + variable)
                                || css.contains("-fx-fill: " + variable)
                                || css.contains("-fx-text-fill: " + variable)
                                || css.contains("-fx-text-background-color: " + variable)
                                || css.contains(variable + ",") || css.contains(variable + ")"),
                        resource + " defines " + variable + " but never reads it");
            }
        }
    }

    private static String read(String resource) {
        try (InputStream in = InvoiceThemeColorsTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing stylesheet on the classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
