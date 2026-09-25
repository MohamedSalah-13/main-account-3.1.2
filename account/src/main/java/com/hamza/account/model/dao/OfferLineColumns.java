package com.hamza.account.model.dao;

import com.hamza.account.model.base.BasePurchasesAndSales;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The columns a sales line and a sales-return line carry for an offer (V85, V86,
 * docs/pricing-and-offers-plan.md ق-ع١): which offer wrote the discount, how much of it is that offer's, and
 * how many of the line's units it covered.
 * Read off the line views for the two mappers; declared in {@code DocumentTableSpec}.
 */
final class OfferLineColumns {

    static final String OFFER_ID = "offer_id";
    static final String OFFER_DISCOUNT = "offer_discount";
    static final String OFFER_QUANTITY = "offer_quantity";
    /** The offer's name as it is now, joined by the two line views - for the screen and the paper. */
    static final String OFFER_NAME = "offer_name";

    private OfferLineColumns() {
    }

    static void read(ResultSet rows, BasePurchasesAndSales line) throws SQLException {
        int offerId = rows.getInt(OFFER_ID);
        line.setOfferId(rows.wasNull() ? null : offerId);
        BigDecimal discount = rows.getBigDecimal(OFFER_DISCOUNT);
        line.setOfferDiscount(discount == null ? BigDecimal.ZERO : discount);
        BigDecimal covered = rows.getBigDecimal(OFFER_QUANTITY);
        line.setOfferQuantity(covered == null ? BigDecimal.ZERO : covered);
        line.setOfferName(rows.getString(OFFER_NAME));
    }
}
