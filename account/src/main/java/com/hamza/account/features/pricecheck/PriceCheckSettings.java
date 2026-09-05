package com.hamza.account.features.pricecheck;

import com.hamza.account.features.invoice.InvoiceItemSelectionService.ScaleBarcodeSettings;

/**
 * What the price-check screen is set up to answer, decided once by whoever hangs the
 * device on the wall and then never again while it is running.
 *
 * @param stockId     the branch's warehouse. A balance is per warehouse, so a screen
 *                    that guessed would report the company's stock to a customer
 *                    standing in one shop.
 * @param priceTier   which of the item's three selling prices is shown. The customer
 *                    in front of the screen does not know which tier they are on, so
 *                    the screen commits to one rather than asking.
 * @param showBalance whether the quantity on hand is shown. It is an internal figure
 *                    and a branch may refuse to put it in front of a customer, which
 *                    is why it is a switch rather than a decision made here.
 * @param showImage   whether the item's picture is shown when it has one.
 * @param showExpiry  whether the nearest expiry date of the stock on hand is shown,
 *                    for items that track batches at all.
 * @param scaleBarcode how a scale's own barcode is recognised - {@code disabled()} for
 *                    a shop with no scale. A weighed product's code carries the weight
 *                    inside it, so without this the screen would answer with the price
 *                    of one kilo for a packet that is not one kilo.
 */
public record PriceCheckSettings(int stockId, int priceTier, boolean showBalance,
                                 boolean showImage, boolean showExpiry,
                                 ScaleBarcodeSettings scaleBarcode) {

    public PriceCheckSettings {
        if (stockId <= 0) {
            throw new IllegalArgumentException("stockId must be a real warehouse");
        }
        // A tier outside 1..3 has no column behind it; falling back to the first is what
        // every price reader in the invoice does with an unknown tier.
        priceTier = priceTier < 1 || priceTier > 3 ? 1 : priceTier;
        scaleBarcode = scaleBarcode == null ? ScaleBarcodeSettings.disabled() : scaleBarcode;
    }
}
