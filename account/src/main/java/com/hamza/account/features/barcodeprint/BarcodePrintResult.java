package com.hamza.account.features.barcodeprint;

/** Counts reported after the complete batch has been accepted by the print service. */
public record BarcodePrintResult(int itemCount, long copies, long labels, String printerName) {
}
