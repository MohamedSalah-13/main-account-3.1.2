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

    /**
     * The whole batch for the preview window, a page per item - the preview beside the table shows the
     * label of one; this is every one of them, drawn as {@link #print} draws them for the batch's
     * printer. It refuses what that preview refuses and every line the printer could not draw, before
     * anything is shown.
     *
     * @param printing how the window sends the batch, on the printer chosen there - through {@link #print}
     */
    public LabelPreviewDocument previewAll(BarcodePrintBatch batch, LabelPreviewDocument.Printing printing)
            throws Exception {
        requireValid(BarcodePrintValidation.forPreview(batch));
        return new LabelPreviewDocument(batch, engine.labelDrawer(batch), printing);
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
