package com.hamza.account.features.audit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditJsonDiffTest {

    @Test
    void flattensNestedObjectsAndClassifiesEveryChange() {
        List<AuditDiffRow> rows = AuditJsonDiff.compare(
                "{\"id\":1,\"name\":\"Tea\",\"address\":{\"city\":\"Cairo\"},\"removed\":7}",
                "{\"id\":1,\"name\":\"Coffee\",\"address\":{\"city\":\"Giza\"},\"added\":9}");

        assertEquals(new AuditDiffRow("id", "1", "1", AuditDiffKind.UNCHANGED), rows.get(0));
        assertEquals(new AuditDiffRow("name", "Tea", "Coffee", AuditDiffKind.CHANGED), rows.get(1));
        assertEquals(new AuditDiffRow("address.city", "Cairo", "Giza", AuditDiffKind.CHANGED), rows.get(2));
        assertEquals(new AuditDiffRow("removed", "7", "", AuditDiffKind.REMOVED), rows.get(3));
        assertEquals(new AuditDiffRow("added", "", "9", AuditDiffKind.ADDED), rows.get(4));
    }

    @Test
    void preservesLegacyTextAndRendersArraysCompactly() {
        assertEquals(List.of(new AuditDiffRow("$", "legacy", "[1,2]", AuditDiffKind.CHANGED)),
                AuditJsonDiff.compare("legacy", "[1,2]"));
    }

    @Test
    void classifiesInsertAndDeleteSnapshots() {
        assertEquals(AuditDiffKind.ADDED,
                AuditJsonDiff.compare(null, "{\"name\":\"Tea\"}").getFirst().kind());
        assertEquals(AuditDiffKind.REMOVED,
                AuditJsonDiff.compare("{\"name\":\"Tea\"}", null).getFirst().kind());
    }
}
