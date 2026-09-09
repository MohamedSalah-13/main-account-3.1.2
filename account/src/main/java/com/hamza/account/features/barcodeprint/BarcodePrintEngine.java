package com.hamza.account.features.barcodeprint;

/** The printer/report boundary, replaceable without changing validation or the JavaFX screen. */
public interface BarcodePrintEngine {
    byte[] previewPng(BarcodePrintBatch batch) throws Exception;

    void print(BarcodePrintBatch batch) throws Exception;
}
