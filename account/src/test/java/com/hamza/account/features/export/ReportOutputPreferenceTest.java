package com.hamza.account.features.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportOutputPreferenceTest {

    @Test
    void keepsExistingInstallsOnSavingA4Reports() {
        assertEquals(ReportOutputMode.SAVE_PDF, ReportOutputMode.fromStoredValue(null));
        assertEquals(ReportOutputMode.SAVE_PDF, ReportOutputMode.fromStoredValue("old-value"));
        assertEquals(ReportPaperSize.A4, ReportPaperSize.fromStoredValue(null));
        assertEquals(ReportPaperSize.A4, ReportPaperSize.fromStoredValue("old-value"));
    }

    @Test
    void readsConfiguredPdfOutputChoices() {
        assertEquals(ReportOutputMode.PRINT_DIRECT,
                ReportOutputMode.fromStoredValue("PRINT_DIRECT"));
        assertEquals(ReportOutputMode.ASK, ReportOutputMode.fromStoredValue("ASK"));
        assertEquals(ReportPaperSize.A5, ReportPaperSize.fromStoredValue("A5"));
    }
}
