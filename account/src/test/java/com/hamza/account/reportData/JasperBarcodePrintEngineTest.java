package com.hamza.account.reportData;

import com.hamza.account.features.barcodeprint.BarcodeLabelOptions;
import com.hamza.account.features.barcodeprint.BarcodeNameOverflow;
import com.hamza.account.features.barcodeprint.BarcodePrintBatch;
import com.hamza.account.features.barcodeprint.BarcodePrintLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JasperBarcodePrintEngineTest {

    @Test
    void previewCompilesTheRealTemplateAndProducesPng() throws Exception {
        BarcodeLabelOptions options = new BarcodeLabelOptions(
                40, 28, false, true, true, true, BarcodeNameOverflow.ELLIPSIS, 24, 8);
        BarcodePrintBatch batch = new BarcodePrintBatch(List.of(
                new BarcodePrintLine("6221234567890", "Preview item", new BigDecimal("12.50"), 1)
        ), "", options);

        byte[] png = new JasperBarcodePrintEngine().previewPng(batch);
        var image = ImageIO.read(new ByteArrayInputStream(png));

        assertNotNull(image);
        assertTrue(image.getWidth() > 0);
        assertTrue(image.getHeight() > 0);
    }

    /**
     * 40x28mm is the one size the template was drawn at, and was the only size tested - while a
     * 38x25mm roll, the commonest there is, failed to compile because the band kept its 76pt on a
     * 71pt page. Every size the validation accepts has to compile, in both label modes.
     */
    @ParameterizedTest
    @CsvSource({
            "10, 10", "20, 10", "25, 15", "30, 20", "38, 25", "40, 25", "40, 28", "50, 25",
            "50, 30", "58, 40", "60, 40", "100, 50", "100, 150", "300, 10", "10, 300", "300, 300"
    })
    void everyAcceptedLabelSizeCompilesAndPreviews(double widthMm, double heightMm) throws Exception {
        var engine = new JasperBarcodePrintEngine();
        for (boolean doubleLabel : new boolean[]{false, true}) {
            BarcodeLabelOptions options = new BarcodeLabelOptions(
                    widthMm, heightMm, doubleLabel, true, true, true, BarcodeNameOverflow.ELLIPSIS, 24, 8);
            BarcodePrintBatch batch = new BarcodePrintBatch(List.of(
                    new BarcodePrintLine("6221234567890", "Preview item", new BigDecimal("12.50"), 1)
            ), "", options);

            var image = ImageIO.read(new ByteArrayInputStream(engine.previewPng(batch)));

            assertNotNull(image, widthMm + "x" + heightMm + " double=" + doubleLabel);
            assertTrue(image.getWidth() > 0 && image.getHeight() > 0);
        }
    }
}
