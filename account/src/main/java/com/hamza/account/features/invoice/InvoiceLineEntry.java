package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.service.ItemUnits;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The one way a line reaches an invoice, from any of its entry surfaces.
 *
 * <p>The form above the table on the standard screen, the table itself on the quick one, and
 * the catalogue picker on both come through here, because everything that makes an added line
 * correct is in these few steps: the line's validation, the expiry date (asked for, then checked
 * against the batches on hand), the repeated-item merge, and the low-stock warning. It lived in
 * {@code BuyController2.addLine}, where the quick screen could only reach it through its host and
 * no test could reach it at all - and the quick screen had once set the fields on its row directly
 * and so skipped every one of these, which is how an item tracked by expiry came to be scannable
 * onto a quick invoice and then refused at save with no way to supply the date.
 *
 * <p>The two things a screen must supply are seams: the expiry question is a dialog, and the
 * low-stock warning a notification. Neither needs a toolkit to be decided, only to be shown.
 */
@Log4j2
public final class InvoiceLineEntry {

    /** Asks for the date of a line tracked by expiry; empty when the question was dismissed. */
    @FunctionalInterface
    public interface ExpiryPrompt {
        Optional<LocalDate> ask(InvoiceExpiryOptions options);
    }

    /**
     * Told how much of an item a sale now holds, in base units, once a line of it is on the
     * invoice - so a warning can say what the sale leaves behind rather than what was there.
     */
    @FunctionalInterface
    public interface LowStockWarning {
        LowStockWarning NONE = (item, onInvoice) -> { };

        void check(ItemsModel item, double baseQuantityOnInvoice);
    }

    /**
     * The shop's settings that shape an added line, read when a line is added rather than when
     * the screen opened, since the settings tab can change them while an invoice is open.
     *
     * @param mergeRepeatedItems   "a repeated item goes on its first line" is on
     * @param scaleBarcodePrefix   the prefix of a scale barcode, whose lines are never merged -
     *                             each is its own weighed quantity; blank when there is none
     * @param allowInsufficientStock selling past the balance is allowed
     */
    public record Settings(boolean mergeRepeatedItems, String scaleBarcodePrefix,
                           boolean allowInsufficientStock) {
    }

    private final DocumentType documentType;
    private final InvoiceLineService<BasePurchasesAndSales> lineService;
    private final InvoiceExpiryService expiryService;
    private final ExpiryPrompt expiryPrompt;
    private final LowStockWarning lowStockWarning;
    private final Supplier<Settings> settings;

    public InvoiceLineEntry(DocumentType documentType,
                            InvoiceLineService<BasePurchasesAndSales> lineService,
                            InvoiceExpiryService expiryService,
                            ExpiryPrompt expiryPrompt,
                            LowStockWarning lowStockWarning,
                            Supplier<Settings> settings) {
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.lineService = Objects.requireNonNull(lineService, "lineService");
        this.expiryService = Objects.requireNonNull(expiryService, "expiryService");
        this.expiryPrompt = Objects.requireNonNull(expiryPrompt, "expiryPrompt");
        this.lowStockWarning = Objects.requireNonNull(lowStockWarning, "lowStockWarning");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Adds a line, or folds it into the line of the same item and unit when the shop merges
     * repeats.
     *
     * @return the row that was added or merged into, or {@code null} when the expiry question
     * was dismissed - the only step that can decline without an error
     */
    public BasePurchasesAndSales add(List<BasePurchasesAndSales> lines, InvoiceLineDraft draft)
            throws Exception {
        Objects.requireNonNull(draft, "draft");
        Settings current = settings.get();
        ItemsModel item = draft.item();
        lineService.validate(lines, draft, current.allowInsufficientStock());

        InvoiceLineDraft dated = draft;
        InvoiceExpiryOptions options = expiryService.optionsFor(item, lines);
        if (options.mode() != InvoiceExpiryOptions.Mode.NOT_REQUIRED) {
            LocalDate date = expiryPrompt.ask(options).orElse(null);
            if (date == null) {
                return null;
            }
            expiryService.validateSelectedDate(options, date,
                    ItemUnits.toBase(draft.quantity(), draft.unit()));
            dated = draft.withExpirationDate(date);
        }

        BasePurchasesAndSales line = lineService.add(lines, dated, merges(current, item),
                current.allowInsufficientStock()).line();
        warnIfStockIsLow(lines, item);
        return line;
    }

    /**
     * Whether a repeat of this item goes onto its existing line. Never for a scale barcode's item:
     * each weighing is a quantity of its own, and two of them folded into one row read as one
     * weighing of the sum. An item with no barcode at all used to reach {@code startsWith} as null.
     */
    static boolean merges(Settings settings, ItemsModel item) {
        if (!settings.mergeRepeatedItems()) {
            return false;
        }
        String prefix = settings.scaleBarcodePrefix();
        String barcode = item == null ? null : item.getBarcode();
        return prefix == null || prefix.isBlank() || barcode == null || !barcode.startsWith(prefix);
    }

    /**
     * Sales only, and not sales returns: a return puts stock back, so a low balance there is not
     * something to warn about. Read after the row is in the table, so the quantity just added is
     * part of what the sale holds. A failure is logged and passed over - the line is added and
     * the sale is fine; only the warning failed.
     */
    private void warnIfStockIsLow(List<BasePurchasesAndSales> lines, ItemsModel item) {
        if (item == null || documentType != DocumentType.SALES) {
            return;
        }
        try {
            lowStockWarning.check(item, lineService.quantityInBase(lines, item.getId()));
        } catch (RuntimeException e) {
            log.error("Could not check the stock level after adding item {}", item.getId(), e);
        }
    }
}
