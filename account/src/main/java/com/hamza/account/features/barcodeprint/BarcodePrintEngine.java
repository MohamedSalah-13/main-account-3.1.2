package com.hamza.account.features.barcodeprint;

import java.awt.image.BufferedImage;
import java.util.function.Function;

/** The printer/report boundary, replaceable without changing validation or the JavaFX screen. */
public interface BarcodePrintEngine {
    byte[] previewPng(BarcodePrintBatch batch) throws Exception;

    void print(BarcodePrintBatch batch) throws Exception;

    /**
     * Draws one line's label as {@link #print} sends it to the batch's printer, when it is asked for -
     * the preview of the whole batch draws the page it shows and no other. Every line {@link #print}
     * would refuse is refused here, before anything is drawn.
     */
    Function<BarcodePrintLine, BufferedImage> labelDrawer(BarcodePrintBatch batch) throws Exception;
}
