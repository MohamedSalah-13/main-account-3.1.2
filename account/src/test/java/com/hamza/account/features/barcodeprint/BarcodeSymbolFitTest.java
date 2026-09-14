package com.hamza.account.features.barcodeprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarcodeSymbolFitTest {

    /** 41 mm at 203 DPI is 328 dots: 123 modules and two 10-module quiet zones fit at two dots each. */
    @Test
    void aThirteenDigitCodeOnTheDefaultLabelGetsTwoDotsAModuleAndFullQuietZones() {
        BarcodeSymbolFit fit = BarcodeSymbolFit.of(123, 328, 203).orElseThrow();

        assertEquals(2, fit.dotsPerModule());
        assertEquals(20, fit.quietZoneDots());
        assertEquals(246, fit.symbolDots());
        assertEquals(286, fit.totalDots());
    }

    @Test
    void refusesRatherThanPrintingOneDotModulesAThermalHeadFillsIn() {
        // 255 modules + 20 quiet: one dot a module would fit 328 dots, and would not scan.
        assertTrue(BarcodeSymbolFit.of(255, 328, 203).isEmpty());
    }

    @Test
    void refusesWhenEvenTheNarrowestReadableModuleDoesNotFit() {
        assertTrue(BarcodeSymbolFit.of(123, 240, 203).isEmpty());
    }

    @Test
    void capsTheModuleSoAShortCodeOnAWideLabelIsNotStretchedAcrossIt() {
        // 0.4 mm at 203 DPI is 3.19 dots: a wide label still gets three.
        assertEquals(3, BarcodeSymbolFit.of(46, 800, 203).orElseThrow().dotsPerModule());
        assertEquals(4, BarcodeSymbolFit.of(46, 1200, 300).orElseThrow().dotsPerModule());
    }

    @Test
    void aFinerHeadNeedsMoreDotsForTheSameReadableWidth() {
        // 0.165 mm at 600 DPI is 3.9 dots, so two would be 0.085 mm.
        assertEquals(5, BarcodeSymbolFit.of(123, 143 * 5, 600).orElseThrow().dotsPerModule());
        assertTrue(BarcodeSymbolFit.of(123, 143 * 3, 600).isEmpty());
    }

    @Test
    void refusesMeaninglessInput() {
        assertTrue(BarcodeSymbolFit.of(0, 328, 203).isEmpty());
        assertTrue(BarcodeSymbolFit.of(123, 0, 203).isEmpty());
        assertTrue(BarcodeSymbolFit.of(123, 328, 0).isEmpty());
    }
}
