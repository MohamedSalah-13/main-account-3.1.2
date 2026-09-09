package com.hamza.account.features.barcodeprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarcodeLabelTextTest {

    @Test
    void ellipsisKeepsTheNameInsideTheConfiguredLimit() {
        BarcodeLabelText.RenderedName rendered = BarcodeLabelText.renderName(
                "Long product name", BarcodeNameOverflow.ELLIPSIS, 8, 10);

        assertEquals("Long pr…", rendered.value());
        assertEquals(10, rendered.fontSize());
        assertTrue(rendered.visible());
    }

    @Test
    void shrinkPreservesTheNameAndReducesItsFont() {
        BarcodeLabelText.RenderedName rendered = BarcodeLabelText.renderName(
                "1234567890", BarcodeNameOverflow.SHRINK, 5, 10);

        assertEquals("1234567890", rendered.value());
        assertEquals(5, rendered.fontSize());
        assertTrue(rendered.visible());
    }

    @Test
    void hideSuppressesOnlyAnOverlongName() {
        BarcodeLabelText.RenderedName rendered = BarcodeLabelText.renderName(
                "123456", BarcodeNameOverflow.HIDE, 5, 10);

        assertEquals("", rendered.value());
        assertFalse(rendered.visible());
    }
}
