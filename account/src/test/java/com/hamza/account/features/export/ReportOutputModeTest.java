package com.hamza.account.features.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportOutputModeTest {

    @Test
    void theStoredNameReadsBackAndAnythingElseSavesAFile() {
        assertEquals(ReportOutputMode.PREVIEW, ReportOutputMode.fromStoredValue("PREVIEW"));
        assertEquals(ReportOutputMode.SAVE_PDF, ReportOutputMode.fromStoredValue(null));
        assertEquals(ReportOutputMode.SAVE_PDF, ReportOutputMode.fromStoredValue("SHOW_IT"));
    }

    /** A printed or previewed report is written to a file that is deleted after it; a saved one is the user's. */
    @Test
    void onlyASavedReportKeepsItsFile() {
        assertTrue(ReportOutputMode.PRINT_DIRECT.usesTemporaryFile());
        assertTrue(ReportOutputMode.PREVIEW.usesTemporaryFile());
        assertFalse(ReportOutputMode.SAVE_PDF.usesTemporaryFile());
        assertFalse(ReportOutputMode.ASK.usesTemporaryFile(), "asking resolves to one of the others first");
    }
}
