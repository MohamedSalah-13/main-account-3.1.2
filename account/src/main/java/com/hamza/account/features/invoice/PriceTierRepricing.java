package com.hamza.account.features.invoice;

import com.hamza.account.features.pricing.ListedPrice;
import com.hamza.account.features.pricing.PriceResolver;
import com.hamza.account.model.base.BasePurchasesAndSales;

import java.util.List;
import java.util.Objects;

/**
 * The lines of an invoice restated at another price tier (V84, docs/pricing-and-offers-plan.md ق-س٢):
 * when the tier box changes, or the customer chosen brings another tier with them.
 * <p>
 * <b>Only a line that took its price from the list moves</b> - its price is still its list price. A line
 * whose price was typed over is the cashier's decision and is left as typed; a return line picked from
 * its invoice carries that invoice's price, which {@code ReturnCostResolver} holds it to; and a line with
 * no list price behind it - one reopened from a document saved before V84 - cannot be told apart from a
 * typed one, so it is left too. How many were left is answered, for the screen to say.
 */
public final class PriceTierRepricing {

    /** How many lines took the new tier's price, and how many were left at the price they had. */
    public record Result(int repriced, int kept) {

        public boolean touchedAnything() {
            return repriced + kept > 0;
        }
    }

    private PriceTierRepricing() {
    }

    /**
     * @param itemPrice the item's own price on a tier - the document family's, as the entry form reads it
     * @param pricing   the currency the screen's figures are in; a list price is offered converted
     */
    public static Result restate(List<? extends BasePurchasesAndSales> lines, int tierId,
                                 PriceResolver.ItemPrice itemPrice, DocumentPricing pricing) {
        Objects.requireNonNull(itemPrice, "itemPrice");
        Objects.requireNonNull(pricing, "pricing");
        int repriced = 0;
        int kept = 0;
        for (BasePurchasesAndSales line : lines) {
            if (InvoiceLineTotals.isPlaceholder(line)) {
                continue;
            }
            if (!atItsListPrice(line)) {
                kept++;
                continue;
            }
            ListedPrice listed = PriceResolver.resolve(line.getItems(), line.getUnitsType(), tierId, itemPrice);
            double price = pricing.fromBase(listed.price());
            if (price <= 0) {
                kept++;
                continue;
            }
            line.setPrice(price);
            InvoiceLineService.applyListed(line, new InvoiceLineDraft.Listed(price, listed.fromFirstTier()));
            InvoiceLineService.recalculate(line);
            repriced++;
        }
        return new Result(repriced, kept);
    }

    /** Whether a line's price is still what the list said for it - the only kind a tier change moves. */
    static boolean atItsListPrice(BasePurchasesAndSales line) {
        return line.getSourceLineId() <= 0
                && line.getListPrice() != null
                && Math.abs(line.getPrice() - line.getListPrice().doubleValue()) < InvoiceLineService.LIST_TOLERANCE;
    }
}
