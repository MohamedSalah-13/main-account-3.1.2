package com.hamza.account.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiftReportDataTest {

    @Test
    void ordinaryXReportShowsExpectationButNeverShowsActualOrDifference() {
        ShiftReportService.ShiftReportData report = report(ShiftReportService.ShiftReportType.X, true);

        assertTrue(report.showExpectedBalance());
        assertFalse(report.showActualBalance());
        assertFalse(report.showDifference());
    }

    @Test
    void blindXReportHidesEveryReconciliationFigure() {
        ShiftReportService.ShiftReportData report = report(ShiftReportService.ShiftReportType.X, false);

        assertFalse(report.showExpectedBalance());
        assertFalse(report.showActualBalance());
        assertFalse(report.showDifference());
    }

    @Test
    void zReportShowsTheClosedShiftReconciliation() {
        ShiftReportService.ShiftReportData report = report(ShiftReportService.ShiftReportType.Z, true);

        assertTrue(report.showExpectedBalance());
        assertTrue(report.showActualBalance());
        assertTrue(report.showDifference());
    }

    private ShiftReportService.ShiftReportData report(
            ShiftReportService.ShiftReportType type, boolean showExpectedBalance) {
        return new ShiftReportService.ShiftReportData(null, null, null, type, showExpectedBalance);
    }
}
