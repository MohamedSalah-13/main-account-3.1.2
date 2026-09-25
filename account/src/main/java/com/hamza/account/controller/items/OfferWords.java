package com.hamza.account.controller.items;

import com.hamza.account.features.offers.Offer;
import com.hamza.account.features.offers.OfferKind;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;

import java.math.BigDecimal;

/**
 * An offer in a few words, wherever one is shown to somebody: the offers list, the price-check screen on the
 * wall, the performance report. One place, so the till's screens and the customer's never describe one offer
 * two ways.
 */
public final class OfferWords {

    private OfferWords() {
    }

    public static String kind(OfferKind kind) {
        return switch (kind) {
            case PERCENT -> text("offer.kind.percent");
            case AMOUNT -> text("offer.kind.amount");
            case PRICE -> text("offer.kind.price");
            case QUANTITY_PRICE -> text("offer.kind.quantity.price");
            case BUY_GET -> text("offer.kind.buy.get");
            case BUNDLE -> text("offer.kind.bundle");
            case INVOICE -> text("offer.kind.invoice");
        };
    }

    /**
     * What the offer gives, in a few words. Every figure stands between words: "2 + 1" in a right-to-left
     * cell reads "1 + 2", which is the opposite offer.
     */
    public static String value(Offer offer) {
        return switch (offer.kind()) {
            case PERCENT -> plain(offer.percent()) + "%";
            case AMOUNT -> Columns.money(offer.amount());
            case PRICE -> Columns.money(offer.offerPrice());
            case QUANTITY_PRICE -> text("offer.value.quantity.price", plain(offer.buyQuantity()),
                    Columns.money(offer.offerPrice()));
            case BUY_GET -> offer.getPercent().compareTo(BigDecimal.valueOf(100)) == 0
                    ? text("offer.value.buy.get.free", plain(offer.buyQuantity()), plain(offer.getQuantity()))
                    : text("offer.value.buy.get.percent", plain(offer.buyQuantity()), plain(offer.getQuantity()),
                            plain(offer.getPercent()));
            case BUNDLE -> text("offer.value.bundle", Columns.money(offer.offerPrice()));
            case INVOICE -> text("offer.value.invoice", Columns.money(offer.threshold()),
                    offer.percent() != null ? plain(offer.percent()) + "%" : Columns.money(offer.amount()));
        };
    }

    static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String text(String key, Object... args) {
        return LanguageManager.getInstance().getString(key, args);
    }
}
