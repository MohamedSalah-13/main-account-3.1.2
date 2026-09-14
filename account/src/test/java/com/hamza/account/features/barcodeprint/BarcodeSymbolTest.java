package com.hamza.account.features.barcodeprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarcodeSymbolTest {

    @Test
    void encodesDigitsCompactlyAndStartsAndEndsOnABar() {
        BarcodeSymbol symbol = BarcodeSymbol.code128("6221234567890").orElseThrow();

        // Start, one digit in B, a switch to C, six pairs, check, stop: 10 * 11 + 13.
        assertEquals(123, symbol.moduleCount());
        assertTrue(symbol.isBar(0));
        assertTrue(symbol.isBar(symbol.moduleCount() - 1));
    }

    @Test
    void cannotCarryArabic() {
        assertTrue(BarcodeSymbol.code128("صنف").isEmpty());
    }
}
