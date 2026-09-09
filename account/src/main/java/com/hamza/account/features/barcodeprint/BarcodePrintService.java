package com.hamza.account.features.barcodeprint;

import java.util.List;
import java.util.Objects;

/** Validates a complete batch before any output is sent, then delegates to the selected engine. */
public final class BarcodePrintService {
    private final BarcodePrintEngine engine;

    public BarcodePrintService(BarcodePrintEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    public byte[] preview(BarcodePrintBatch batch) throws Exception {
        requireValid(BarcodePrintValidation.forPreview(batch));
        return engine.previewPng(batch);
    }

    public BarcodePrintResult print(BarcodePrintBatch batch) throws Exception {
        requireValid(BarcodePrintValidation.forPrint(batch));
        engine.print(batch);
        return new BarcodePrintResult(batch.lines().size(), batch.totalCopies(),
                batch.totalLabels(), batch.printerName());
    }

    private void requireValid(List<BarcodePrintProblem> problems) throws BarcodePrintValidationException {
        if (!problems.isEmpty()) {
            throw new BarcodePrintValidationException(problems);
        }
    }
}
