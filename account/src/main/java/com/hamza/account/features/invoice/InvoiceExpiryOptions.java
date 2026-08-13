package com.hamza.account.features.invoice;

import java.time.LocalDate;
import java.util.List;

/** Describes how an expiry-tracked line obtains its date. */
public record InvoiceExpiryOptions(Mode mode, List<Batch> batches) {

    public InvoiceExpiryOptions {
        batches = List.copyOf(batches == null ? List.of() : batches);
    }

    public static InvoiceExpiryOptions notRequired() {
        return new InvoiceExpiryOptions(Mode.NOT_REQUIRED, List.of());
    }

    public static InvoiceExpiryOptions manualEntry() {
        return new InvoiceExpiryOptions(Mode.MANUAL_ENTRY, List.of());
    }

    public static InvoiceExpiryOptions existingBatches(List<Batch> batches) {
        return new InvoiceExpiryOptions(Mode.EXISTING_BATCH, batches);
    }

    public enum Mode {
        NOT_REQUIRED,
        MANUAL_ENTRY,
        EXISTING_BATCH
    }

    public record Batch(LocalDate expirationDate, double availableBaseQuantity) {
    }
}
