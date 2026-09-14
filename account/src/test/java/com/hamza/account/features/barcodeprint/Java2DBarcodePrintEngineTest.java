package com.hamza.account.features.barcodeprint;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Java2DBarcodePrintEngineTest {

    @Test
    void previewUsesTheRequestedPhysicalLabelDimensionsAtTheRendererDensity() throws Exception {
        BarcodeLabelOptions options = options(40, 28, false);
        BarcodePrintBatch batch = new BarcodePrintBatch(List.of(line()), "", options);

        var image = ImageIO.read(new ByteArrayInputStream(new Java2DBarcodePrintEngine().previewPng(batch)));

        assertNotNull(image);
        assertEquals(Math.round(40 * Java2DBarcodePrintEngine.RENDER_DPI / 25.4d), image.getWidth());
        assertEquals(Math.round(28 * Java2DBarcodePrintEngine.RENDER_DPI / 25.4d), image.getHeight());
    }

    @Test
    void rendersBothLabelsOnOnePageWhenDoubleLabelIsSelected() throws Exception {
        var image = Java2DBarcodePrintEngine.render(line(), options(40, 28, true));

        assertTrue(hasInk(image, 0, image.getHeight() / 2));
        assertTrue(hasInk(image, image.getHeight() / 2, image.getHeight()));
    }

    private static BarcodePrintLine line() {
        return new BarcodePrintLine("6221234567890", "صنف اختبار طويل", new BigDecimal("12.50"), 1);
    }

    private static BarcodeLabelOptions options(double width, double height, boolean doubleLabel) {
        return new BarcodeLabelOptions(width, height, doubleLabel, true, true, true,
                BarcodeNameOverflow.ELLIPSIS, 24, 8);
    }

    private static boolean hasInk(java.awt.image.BufferedImage image, int startY, int endY) {
        for (int y = startY; y < endY; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00FFFFFF) != 0x00FFFFFF) return true;
            }
        }
        return false;
    }
}
