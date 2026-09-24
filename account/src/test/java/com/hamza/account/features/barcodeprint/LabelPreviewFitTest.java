package com.hamza.account.features.barcodeprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LabelPreviewFitTest {

    /** A 41x28mm double label at 203 DPI is 327 by 224 dots. */
    private static final double LABEL_WIDTH = 327;

    @Test
    void aLabelLargerThanItsCardIsShrunkToTheCard() {
        assertEquals(267, LabelPreviewFit.side(267, LABEL_WIDTH));
    }

    @Test
    void aLabelSmallerThanItsCardIsShownPixelForPixelNotEnlarged() {
        assertEquals(LABEL_WIDTH, LabelPreviewFit.side(400, LABEL_WIDTH));
    }

    /** An {@code ImageView} reads a fit of zero as the image's own size - the overflow itself. */
    @Test
    void aCardWithNoSizeYetStillHoldsTheImageToOnePixel() {
        assertEquals(1, LabelPreviewFit.side(0, LABEL_WIDTH));
        assertEquals(1, LabelPreviewFit.side(-28, LABEL_WIDTH), "a card narrower than its own padding");
    }

    @Test
    void withNoLabelYetTheFitIsTheCard() {
        assertEquals(267, LabelPreviewFit.side(267, 0));
    }
}
