package com.hamza.account.reportData;

import com.hamza.account.features.barcodeprint.BarcodeLabelOptions;
import com.hamza.account.features.barcodeprint.BarcodeNameOverflow;
import com.hamza.account.features.barcodeprint.BarcodePrintBatch;
import com.hamza.account.features.barcodeprint.BarcodePrintLine;
import org.junit.jupiter.api.Test;

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
}
