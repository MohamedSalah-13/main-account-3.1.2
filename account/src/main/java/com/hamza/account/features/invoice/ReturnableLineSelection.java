package com.hamza.account.features.invoice;

import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;

import java.time.LocalDate;

/**
 * One line of a source invoice as a "return from this invoice" picker offers it - what
 * {@link ReturnLineSelectionService#selectableLines} produces, one per original line.
 *
 * @param sourceLineId    the original line's own id - what {@code ReturnCostResolver}
 *                        and {@code ReturnSourceWriter} need to find their way back to
 *                        it; stamped onto the row {@link #draftFor} does not itself set
 * @param item            the sold item
 * @param unit            the unit that line was in
 * @param soldQuantity    what that line itself sold, in {@link #unit}
 * @param price           what that line charged per {@link #unit}
 * @param buyPrice        what that line's items cost, per {@link #unit} - preserved by
 *                        {@code ReturnCostResolver} once the return is saved
 * @param remainingBaseQuantity what is left to return of this <em>item</em> across the
 *                        whole source invoice, in base units - not this line alone, and
 *                        not reduced within one dialog session as sibling lines of the
 *                        same item are picked; {@code ReturnGuard} stays the
 *                        authoritative check at save time, exactly as it already is for
 *                        the quantity a picked expiry batch reports
 * @param expirationDate  the line's own expiry date, or {@code null} if the item does
 *                        not track one
 */
public record ReturnableLineSelection(
        int sourceLineId, ItemsModel item, UnitsModel unit, double soldQuantity,
        double price, double soldDiscount, double buyPrice,
        double remainingBaseQuantity, LocalDate expirationDate) {

    /**
     * The draft {@code InvoiceLineService.add} needs, for the given quantity in
     * {@link #unit} - at the source line's own price, and carrying its proportional
     * share of the source line's discount.
     * <p>
     * The discount matters as much as the price and is easier to forget: it belongs to
     * the whole sold line, so returning 2 of 5 refunds two fifths of it. Refunding the
     * full price with no discount at all - which is what this did before the share was
     * computed - hands back more than was ever charged.
     */
    public InvoiceLineDraft draftFor(double quantity) {
        return new InvoiceLineDraft(item, unit, quantity, price,
                discountShareFor(quantity), expirationDate);
    }

    /** The part of {@link #soldDiscount} that belongs to {@code quantity} of this line. */
    public double discountShareFor(double quantity) {
        if (soldDiscount == 0 || soldQuantity <= 0) {
            return 0;
        }
        return MoneyMath.asDouble(MoneyMath.multiply(soldDiscount, quantity / soldQuantity));
    }
}
