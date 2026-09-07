package com.hamza.account.features.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditJsonFormatterTest {

    @Test
    void prettyPrintsJsonForTheDetailPane() {
        String displayed = AuditJsonFormatter.display("{\"id\":3,\"name\":\"Tea\"}");

        assertTrue(displayed.contains(System.lineSeparator()));
        assertTrue(displayed.contains("\"name\" : \"Tea\""));
    }

    @Test
    void preservesLegacyPlainTextAndBlankValues() {
        assertEquals("legacy value", AuditJsonFormatter.display("legacy value"));
        assertEquals("", AuditJsonFormatter.display(null));
    }
}
