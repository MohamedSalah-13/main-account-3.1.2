package com.hamza.account.features.barcodeprint;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarcodePrintValidationTest {

    @Test
    void previewDoesNotRequirePrinter() {
        BarcodePrintBatch batch = new BarcodePrintBatch(List.of(line("123", 2, "5.00")), "", options());

        assertTrue(BarcodePrintValidation.forPreview(batch).isEmpty());
    }

    @Test
    void printRequiresPrinter() {
        BarcodePrintBatch batch = new BarcodePrintBatch(List.of(line("123", 1, "5.00")), "", options());

        assertEquals(List.of(BarcodePrintProblem.batch(BarcodePrintProblem.Type.MISSING_PRINTER)),
                BarcodePrintValidation.forPrint(batch));
    }

    @Test
    void reportsTheExactRowsWithInvalidProductData() {
        BarcodePrintBatch batch = new BarcodePrintBatch(List.of(
                line("123", 1, "5.00"),
                line(" ", 1, "5.00"),
                line("789", 0, "-1.00")
        ), "Label printer", options());

        assertEquals(List.of(
                BarcodePrintProblem.row(BarcodePrintProblem.Type.MISSING_BARCODE, 2),
                BarcodePrintProblem.row(BarcodePrintProblem.Type.INVALID_COPIES, 3),
                BarcodePrintProblem.row(BarcodePrintProblem.Type.INVALID_PRICE, 3)
        ), BarcodePrintValidation.forPrint(batch));
    }

    @Test
    void rejectsInvalidLabelAndNameSettings() {
        BarcodeLabelOptions invalid = new BarcodeLabelOptions(
                9, 40, false, true, true, true, null, 0, 40);
        BarcodePrintBatch batch = new BarcodePrintBatch(
                List.of(line("123", 1, "5.00")), "", invalid);

        assertEquals(List.of(BarcodePrintProblem.batch(BarcodePrintProblem.Type.INVALID_LABEL_SIZE)),
                BarcodePrintValidation.forPreview(batch));

        BarcodeLabelOptions invalidName = new BarcodeLabelOptions(
                50, 30, false, true, true, true, null, 0, 40);
        BarcodePrintBatch nameBatch = new BarcodePrintBatch(
                List.of(line("123", 1, "5.00")), "", invalidName);
        assertEquals(List.of(BarcodePrintProblem.batch(BarcodePrintProblem.Type.INVALID_NAME_SETTINGS)),
                BarcodePrintValidation.forPreview(nameBatch));
    }

    private BarcodePrintLine line(String barcode, int copies, String price) {
        return new BarcodePrintLine(barcode, "Item", new BigDecimal(price), copies);
    }

    private BarcodeLabelOptions options() {
        return new BarcodeLabelOptions(50, 30, false, true, true, true,
                BarcodeNameOverflow.ELLIPSIS, 30, 10);
    }
}
