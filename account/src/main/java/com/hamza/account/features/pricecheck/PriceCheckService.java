package com.hamza.account.features.pricecheck;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.invoice.InvoiceItemSelection;
import com.hamza.account.features.invoice.InvoiceItemSelectionService;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.service.CardItemService;
import com.hamza.account.service.ItemsService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * Answers one question: what does this barcode cost?
 * <p>
 * <b>It resolves nothing itself.</b> The price of a scanned code is already decided by
 * {@link InvoiceItemSelectionService} - which code table the barcode lives in, which unit
 * it belongs to, whether that unit has a price of its own or scales the item's, and what a
 * scale's barcode means - and a second implementation of those rules here would be a
 * second answer to compare against the till's. What this class adds is the part an invoice
 * has no use for: the nearest expiry date, and turning a refusal into an answer the screen
 * can show, because a customer scanning an unknown packet is not an error to report.
 * <p>
 * Nothing here writes. There is no permission check per scan for the same reason - the
 * screen asks {@link #requireAccess()} once, when it opens.
 */
public final class PriceCheckService {

    private final ItemSelector selector;
    private final ExpiryLookup expiryLookup;

    public PriceCheckService(ItemsService itemsService, CardItemService cardItemService) {
        this(selectorOver(new InvoiceItemSelectionService(DocumentType.SALES, itemsService,
                        PriceCheckService::sellPriceOf)),
                cardItemService::expiryBalancesByItem);
    }

    PriceCheckService(ItemSelector selector, ExpiryLookup expiryLookup) {
        this.selector = Objects.requireNonNull(selector, "selector");
        this.expiryLookup = Objects.requireNonNull(expiryLookup, "expiryLookup");
    }

    private static ItemSelector selectorOver(InvoiceItemSelectionService selection) {
        return (barcode, settings) -> selection.selectByBarcode(barcode, settings.stockId(),
                settings.priceTier(), settings.scaleBarcode());
    }

    /**
     * The screen is behind its own key rather than {@code items.show}: that one opens the
     * item list, where the buying price and the cost of the stock are - the last thing to
     * leave on a screen hanging on a shop wall.
     */
    public static void requireAccess() throws DaoException {
        AuthorizationGuard.require(AppPermissions.ITEMS_PRICE_CHECK);
    }

    public PriceCheckResult lookup(String code, PriceCheckSettings settings) throws DaoException {
        Objects.requireNonNull(settings, "settings");
        if (code == null || code.isBlank()) {
            return new PriceCheckResult.NotFound("");
        }
        String scanned = code.trim();

        InvoiceItemSelection resolved;
        try {
            resolved = selector.select(scanned, settings);
        } catch (UserValidationException noPrice) {
            return new PriceCheckResult.NotFound(scanned);
        }

        ItemsModel item = resolved.item();
        return new PriceCheckResult.Found(
                item.getId(),
                item.getNameItem(),
                resolved.selectedUnit().getUnit_name(),
                resolved.price(),
                resolved.quantity(),
                resolved.total(),
                settings.showBalance() ? resolved.balance() : 0,
                resolved.scaleBarcode(),
                nearestExpiry(item, settings),
                settings.showImage() ? item.getItem_image() : null);
    }

    /**
     * The earliest date still holding stock in this warehouse, which is the batch a
     * customer asking "when does it expire" is about to be handed. The query already
     * drops exhausted batches and orders by date, so the first entry is the answer.
     */
    private LocalDate nearestExpiry(ItemsModel item, PriceCheckSettings settings) throws DaoException {
        if (!settings.showExpiry() || !item.isHasValidate()) {
            return null;
        }
        Map<LocalDate, Double> balances = expiryLookup.balancesFor(settings.stockId(), item.getId());
        if (balances == null || balances.isEmpty()) {
            return null;
        }
        return balances.keySet().stream().filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null);
    }

    /** The item's price for a tier, exactly as {@code SalesInvoice} reads it. */
    private static double sellPriceOf(ItemsModel item, int priceTier) {
        return switch (priceTier) {
            case 2 -> item.getSelPrice2();
            case 3 -> item.getSelPrice3();
            default -> item.getSelPrice1();
        };
    }

    /** Remaining stock per expiry date for one item in one warehouse, in base units. */
    @FunctionalInterface
    public interface ExpiryLookup {
        Map<LocalDate, Double> balancesFor(int stockId, int itemId) throws DaoException;
    }

    /**
     * Resolving a scanned code into an item, a unit and a price - the invoice's own rules,
     * behind an interface so this service can be tested without a database.
     */
    @FunctionalInterface
    interface ItemSelector {
        InvoiceItemSelection select(String barcode, PriceCheckSettings settings) throws DaoException;
    }
}
