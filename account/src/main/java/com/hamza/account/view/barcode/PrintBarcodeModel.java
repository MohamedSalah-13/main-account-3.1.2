package com.hamza.account.view.barcode;

import com.hamza.account.features.pricing.PriceTiers;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.domain.ItemsModel;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Mutable row state for the JavaFX print table; deliberately contains no JavaFX properties.
 * <p>
 * A label prints one price, and which one is the screen's choice of tier (V84): a row built from an item
 * carries the item's price on each of the three, and {@link #showTier} picks the one printed - tier 1's
 * where the chosen tier has none, as an invoice line falls back to it
 * (docs/pricing-and-offers-plan.md ق-س٣). It used to print tier 1's whatever the shop sold at.
 */
public final class PrintBarcodeModel {
    private final String barcode;
    private final String name;
    /** The item's price on each tier, tier 1 first - or the one price a row was built with, three times. */
    private final List<BigDecimal> tierPrices;
    private BigDecimal price;
    private int quantity;

    public PrintBarcodeModel(String barcode, String name, double price) {
        this(barcode, name, MoneyMath.money(price), 1);
    }

    public PrintBarcodeModel(String barcode, String name, BigDecimal price, int quantity) {
        this(barcode, name, List.of(money(price), money(price), money(price)), quantity);
    }

    private PrintBarcodeModel(String barcode, String name, List<BigDecimal> tierPrices, int quantity) {
        this.barcode = Objects.requireNonNullElse(barcode, "").trim();
        this.name = Objects.requireNonNullElse(name, "").trim();
        this.tierPrices = List.copyOf(tierPrices);
        this.price = this.tierPrices.getFirst();
        this.quantity = quantity;
    }

    /** A label for an item's own code, carrying its price on each tier and showing tier 1's. */
    public static PrintBarcodeModel of(ItemsModel item) {
        List<BigDecimal> prices = PriceTiers.IDS.stream()
                .map(tier -> money(BigDecimal.valueOf(PriceTiers.itemPrice(item, tier))))
                .toList();
        return new PrintBarcodeModel(item.getBarcode(), item.getNameItem(), prices, 1);
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
