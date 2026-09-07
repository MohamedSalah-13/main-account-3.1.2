package com.hamza.account.features.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Pretty-prints stored JSON for the detail pane while preserving legacy non-JSON text. */
public final class AuditJsonFormatter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private AuditJsonFormatter() {
    }

    public static String display(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(JSON.readTree(value));
        } catch (JsonProcessingException ignored) {
            return value;
        }
    }
}
