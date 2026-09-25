package com.hamza.account.features.invoice;

import com.hamza.account.features.offers.OfferEngine;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.service.ItemUnits;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the invoice screen says under its lines about an offer the customer is close to
 * (docs/pricing-and-offers-plan.md ق-ع٤): the units that complete a quantity group, the unit a "buy and get"
 * would give, a gift earned and not on the invoice, a bundle's missing component, and what is left to spend to
 * reach an invoice offer. A sentence per hint, the item and the unit named - the
 * unit the offer counts in, or the item's base unit - and every figure between words, never beside a sign
 * that the right-to-left line would move.
 */
public final class InvoiceOfferHints {

    private InvoiceOfferHints() {
    }

    /** An item by id - on the invoice already, or the gift, which may not be. */
    @FunctionalInterface
    public interface Items {
        ItemsModel item(int itemId) throws DaoException;
    }

    public static List<String> sentences(List<OfferEngine.Hint> hints, Items items) throws DaoException {
        Set<String> sentences = new LinkedHashSet<>();
        for (OfferEngine.Hint hint : hints) {
            if (hint.kind() == OfferEngine.HintKind.SPEND) {
                sentences.add(LanguageManager.getInstance().getString("invoice.offer.hint.spend",
                        Columns.money(hint.missing()), hint.offer().name()));
                continue;
            }
            ItemsModel item = items.item(hint.itemId());
            if (item == null) {
                continue;
            }
            String quantity = plain(hint.missing());
            String unit = unitName(item, hint.unitId());
            String offer = hint.offer().name();
            String key = switch (hint.kind()) {
                case COMPLETE -> "invoice.offer.hint.complete";
                case FREE -> "invoice.offer.hint.free";
                case GIFT -> "invoice.offer.hint.gift";
                case BUNDLE -> "invoice.offer.hint.bundle";
                case SPEND -> throw new IllegalStateException("said above");
            };
            sentences.add(LanguageManager.getInstance().getString(key, quantity, unit, item.getNameItem(), offer));
        }
        return new ArrayList<>(sentences);
    }

    private static String unitName(ItemsModel item, Integer unitId) {
        if (unitId != null) {
            for (UnitsModel unit : ItemUnits.unitsFor(item)) {
                if (unit.getUnit_id() == unitId) {
                    return unit.getUnit_name();
                }
            }
        }
        UnitsModel base = ItemUnits.baseUnit(item);
        return base == null || base.getUnit_name() == null ? "" : base.getUnit_name();
    }

    static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
