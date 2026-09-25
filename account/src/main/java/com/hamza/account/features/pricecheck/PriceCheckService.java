package com.hamza.account.features.pricecheck;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.invoice.InvoiceItemSelection;
import com.hamza.account.features.invoice.InvoiceItemSelectionService;
import com.hamza.account.features.invoice.InvoiceOffers;
import com.hamza.account.features.offers.Offer;
import com.hamza.account.features.offers.OfferEngine;
import com.hamza.account.features.offers.OfferPriceTag;
import com.hamza.account.features.offers.OfferService;
import com.hamza.account.features.pricing.PriceTiers;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.service.CardItemService;
import com.hamza.account.service.ItemUnits;
import com.hamza.account.service.ItemsService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Answers one question: what does this barcode cost?
 * <p>
 * <b>It resolves nothing itself.</b> The price of a scanned code is already decided by
 * {@link InvoiceItemSelectionService} - which code table the barcode lives in, which unit
 * it belongs to, whether that unit has a price of its own or scales the item's, and what a
 * scale's barcode means - and a second implementation of those rules here would be a
 * second answer to compare against the till's. What this class adds is the part an invoice
 * has no use for: the nearest expiry date, turning a refusal into an answer the screen
 * can show, because a customer scanning an unknown packet is not an error to report - and
 * what an offer in force says about the unit, which is {@code OfferPriceTag}'s answer over
 * the engine's own rules, so the wall cannot promise what the till will not give.
 * <p>
 * Nothing here writes. There is no permission check per scan for the same reason - the
 * screen asks {@link #requireAccess()} once, when it opens.
 */
@Log4j2
public final class PriceCheckService {

    private final ItemSelector selector;
    private final ExpiryLookup expiryLookup;
    private final OfferLookup offerLookup;

    public PriceCheckService(ItemsService itemsService, CardItemService cardItemService) {
        this(selectorOver(new InvoiceItemSelectionService(DocumentType.SALES, itemsService,
                        PriceCheckService::sellPriceOf)),
                cardItemService::expiryBalancesByItem,
                offersFrom(() -> com.hamza.account.controller.others.ServiceRegistry.get(OfferService.class)));
    }

    PriceCheckService(ItemSelector selector, ExpiryLookup expiryLookup) {
        this(selector, expiryLookup, (resolved, settings) -> Optional.empty());
    }

    PriceCheckService(ItemSelector selector, ExpiryLookup expiryLookup, OfferLookup offerLookup) {
        this.selector = Objects.requireNonNull(selector, "selector");
        this.expiryLookup = Objects.requireNonNull(expiryLookup, "expiryLookup");
        this.offerLookup = Objects.requireNonNull(offerLookup, "offerLookup");
    }

    /**
     * The offers in force today at the screen's tier, read with each scan - a handful of rows - so an offer
     * switched on at the till reaches the wall without restarting it. Nothing without the add-on.
     */
    private static OfferLookup offersFrom(Supplier<OfferService> offers) {
        InvoiceOffers.JdbcGroups groups = new InvoiceOffers.JdbcGroups();
        return (resolved, settings) -> {
            OfferService service = offers.get();
            if (service == null || !service.enabled()) {
                return Optional.empty();
            }
            List<Offer> inForce = service.inForce();
            if (inForce.isEmpty()) {
                return Optional.empty();
            }
            ItemsModel item = resolved.item();
            UnitsModel unit = resolved.selectedUnit();
            InvoiceOffers.ItemGroups itemGroups = groups.groupsOf(List.of(item.getId()))
                    .getOrDefault(item.getId(), new InvoiceOffers.ItemGroups(0, 0));
            OfferEngine.Line line = new OfferEngine.Line(0, item.getId(), unit.getUnit_id(),
                    itemGroups.subGroupId(), itemGroups.mainGroupId(), BigDecimal.valueOf(ItemUnits.factor(unit)),
                    BigDecimal.ONE, BigDecimal.valueOf(resolved.price()));
            return OfferPriceTag.of(inForce, line, LocalDate.now(), settings.priceTier());
        };
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
                settings.showImage() ? item.getItem_image() : null,
                offerFor(resolved, settings));
    }

    /**
     * What an offer says about the scanned unit. A failure to read the offers is logged and answers none: the
     * customer still gets the price, which is what the screen is for.
     */
    private OfferPriceTag offerFor(InvoiceItemSelection resolved, PriceCheckSettings settings) {
        try {
            return offerLookup.tagFor(resolved, settings).orElse(null);
        } catch (Exception unreadable) {
            log.error("price check could not read the offers", unreadable);
            return null;
        }
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

    /** The item's price for a tier, exactly as {@code SalesInvoice} reads it - both ask {@link PriceTiers}. */
    private static double sellPriceOf(ItemsModel item, int priceTier) {
        return PriceTiers.itemPrice(item, priceTier);
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
    /** What an offer in force says about a scanned unit at the screen's tier. */
    @FunctionalInterface
    interface OfferLookup {
        Optional<OfferPriceTag> tagFor(InvoiceItemSelection resolved, PriceCheckSettings settings) throws Exception;
    }

    @FunctionalInterface
    interface ItemSelector {
        InvoiceItemSelection select(String barcode, PriceCheckSettings settings) throws DaoException;
    }
}
