package com.hamza.account.view.barcode;

import com.hamza.account.features.invoice.InvoiceOffers;
import com.hamza.account.features.offers.Offer;
import com.hamza.account.features.offers.OfferEngine;
import com.hamza.account.features.offers.OfferPriceTag;
import com.hamza.account.features.pricing.PriceTiers;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.service.ItemUnits;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable row state for the JavaFX print table; deliberately contains no JavaFX properties.
 * <p>
 * A label prints one price, and which one is the screen's choice of tier (V84): a row built from an item
 * carries the item's price on each of the three, and {@link #showTier} picks the one printed - tier 1's
 * where the chosen tier has none, as an invoice line falls back to it
 * (docs/pricing-and-offers-plan.md ق-س٣). It used to print tier 1's whatever the shop sold at.
 * <p>
 * A row built from an item may also carry an offer's price for one of its base unit (phase E): what
 * {@link OfferPriceTag} answers at the tier shown, the list price then printed struck through beside it. Only
 * an offer with a price for one unit - "3 for 100" changes no unit's price, and is no price on a shelf.
 */
public final class PrintBarcodeModel {
    private final String barcode;
    private final String name;
    /** The item's price on each tier, tier 1 first - or the one price a row was built with, three times. */
    private final List<BigDecimal> tierPrices;
    private BigDecimal price;
    private int quantity;
    /** The item and its base unit an offer is asked about; null for a row built from a code and a price alone. */
    private final Integer itemId;
    private final int unitId;
    /** The offer's price for one unit at the tier shown, or null. */
    private BigDecimal offerPrice;

    public PrintBarcodeModel(String barcode, String name, double price) {
        this(barcode, name, MoneyMath.money(price), 1);
    }

    public PrintBarcodeModel(String barcode, String name, BigDecimal price, int quantity) {
        this(barcode, name, List.of(money(price), money(price), money(price)), quantity);
    }

    private PrintBarcodeModel(String barcode, String name, List<BigDecimal> tierPrices, int quantity) {
        this(barcode, name, tierPrices, quantity, null, 0);
    }

    private PrintBarcodeModel(String barcode, String name, List<BigDecimal> tierPrices, int quantity, Integer itemId,
                              int unitId) {
        this.barcode = Objects.requireNonNullElse(barcode, "").trim();
        this.name = Objects.requireNonNullElse(name, "").trim();
        this.tierPrices = List.copyOf(tierPrices);
        this.price = this.tierPrices.getFirst();
        this.quantity = quantity;
        this.itemId = itemId;
        this.unitId = unitId;
    }

    /** A label for an item's own code, carrying its price on each tier and showing tier 1's. */
    public static PrintBarcodeModel of(ItemsModel item) {
        List<BigDecimal> prices = PriceTiers.IDS.stream()
                .map(tier -> money(BigDecimal.valueOf(PriceTiers.itemPrice(item, tier))))
                .toList();
        UnitsModel base = ItemUnits.baseUnit(item);
        return new PrintBarcodeModel(item.getBarcode(), item.getNameItem(), prices, 1, item.getId(),
                base == null ? 0 : base.getUnit_id());
    }

    /** The item an offer is asked about, or null for a row that names none. */
    public Integer getItemId() {
        return itemId;
    }

    /**
     * Asks the offers in force what one base unit costs at {@code tierId}, the row's current price being the
     * tier's - so it is asked after {@link #showTier}. {@code groups} holds the item's two groups.
     */
    public void showOffer(Collection<Offer> offers, Map<Integer, InvoiceOffers.ItemGroups> groups, LocalDate day,
                          int tierId) {
        offerPrice = null;
        if (itemId == null || offers.isEmpty()) {
            return;
        }
        InvoiceOffers.ItemGroups itemGroups = groups.getOrDefault(itemId, new InvoiceOffers.ItemGroups(0, 0));
        OfferEngine.Line oneUnit = new OfferEngine.Line(0, itemId, unitId, itemGroups.subGroupId(),
                itemGroups.mainGroupId(), BigDecimal.ONE, BigDecimal.ONE, price);
        OfferPriceTag.of(offers, oneUnit, day, tierId).filter(OfferPriceTag::hasPrice)
                .ifPresent(tag -> offerPrice = tag.offerPrice());
    }

    /** Prints the tier's price again, whatever an offer said. */
    public void clearOffer() {
        offerPrice = null;
    }

    /** The offer's price for one unit, or null. */
    public BigDecimal getOfferPrice() {
        return offerPrice;
    }

    /** What the label prints: the offer's price where one is shown, the tier's otherwise. */
    public BigDecimal getPrintedPrice() {
        return offerPrice == null ? price : offerPrice;
    }

    /** Prints the price of {@code tierId} - tier 1's where that tier has none. */
    public void showTier(int tierId) {
        BigDecimal onTier = tierPrices.get(PriceTiers.orFirst(tierId) - 1);
        price = onTier.signum() > 0 ? onTier : tierPrices.getFirst();
    }

    private static BigDecimal money(BigDecimal value) {
        return MoneyMath.money(value == null ? BigDecimal.ZERO : value);
    }

    public String getBarcode() {
        return barcode;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
