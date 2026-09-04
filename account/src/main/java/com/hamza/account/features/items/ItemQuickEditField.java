package com.hamza.account.features.items;

import com.hamza.account.model.domain.ItemsModel;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The fields the items list lets an operator change in place, and what each one will
 * accept.
 * <p>
 * It replaces a chain of {@code if ("buy_price".equals(fieldType))} in the screen, which
 * carried four copies of the same rollback and answered to a string literal - a typo in
 * which was a field that silently did nothing. Here the field is a value, the read and the
 * write are the enum's own business, and {@link #reject(ItemsModel, double)} is a plain
 * function that a test can put every boundary through without a JavaFX toolkit.
 * <p>
 * <b>These are hints, not the rule.</b> The rule is
 * {@code ItemsService.requireSellAboveBuy}, applied where the row is actually written, and
 * this enum is deliberately stated to agree with it: a price the table accepts and the
 * service then refuses is a screen that lies about what it will save, which is what the
 * old {@code newValue > item.getSelPrice1()} did - it allowed a buy price equal to the
 * sell price that the service rejects.
 */
public enum ItemQuickEditField {

    BUY_PRICE(ItemsModel::getBuyPrice, ItemsModel::setBuyPrice) {
        @Override
        public Rejection reject(ItemsModel item, double newValue) {
            if (outOfRange(newValue)) return Rejection.OUT_OF_RANGE;
            // The sell price has to stay strictly above the buy price - equal is a sale
            // at cost, which the service refuses, so the table must refuse it too.
            return newValue >= item.getSelPrice1() ? Rejection.SELL_NOT_ABOVE_BUY : null;
        }
    },
    SELL_PRICE_1(ItemsModel::getSelPrice1, ItemsModel::setSelPrice1) {
        @Override
        public Rejection reject(ItemsModel item, double newValue) {
            return rejectSellPrice(item, newValue);
        }
    },
    /**
     * The second and third tiers are the same price for a different customer, so they are
     * held to the same floor: a tier below cost is a loss the moment that customer buys.
     * <p>
     * Zero is the exception and means the tier is not set - the item screen leaves both
     * fields blank and its "clear prices" button empties them, so a table that refused
     * zero would be the one place in the application where a tier could not be taken back
     * off an item.
     */
    SELL_PRICE_2(ItemsModel::getSelPrice2, ItemsModel::setSelPrice2) {
        @Override
        public Rejection reject(ItemsModel item, double newValue) {
            return newValue == 0 ? null : rejectSellPrice(item, newValue);
        }
    },
    SELL_PRICE_3(ItemsModel::getSelPrice3, ItemsModel::setSelPrice3) {
        @Override
        public Rejection reject(ItemsModel item, double newValue) {
            return newValue == 0 ? null : rejectSellPrice(item, newValue);
        }
    };

    /** Why a typed value was not accepted. The screen turns each into its own sentence. */
    public enum Rejection {
        /** Larger than any price a business states, and a sign of a slipped decimal point. */
        OUT_OF_RANGE,
        /** The sale would be at or below cost. */
        SELL_NOT_ABOVE_BUY
    }

    /**
     * Above this a figure is a typing accident rather than a price - a slipped decimal
     * point or a barcode typed into a price cell. It is a sanity bound, not a business
     * rule, which is why it is the same number for every price field.
     */
    public static final double MAXIMUM = 1_000_000_000_000.0;

    private final Function<ItemsModel, Double> reader;
    private final BiConsumer<ItemsModel, Double> writer;

    ItemQuickEditField(Function<ItemsModel, Double> reader, BiConsumer<ItemsModel, Double> writer) {
        this.reader = reader;
        this.writer = writer;
    }

    /** Why this value cannot be stored on this item, or {@code null} if it can. */
    public abstract Rejection reject(ItemsModel item, double newValue);

    public double read(ItemsModel item) {
        return reader.apply(item);
    }

    /** Writes the value onto the model. Also how the screen puts the old value back. */
    public void write(ItemsModel item, double value) {
        writer.accept(item, value);
    }

    static Rejection rejectSellPrice(ItemsModel item, double newValue) {
        if (outOfRange(newValue)) return Rejection.OUT_OF_RANGE;
        return newValue <= item.getBuyPrice() ? Rejection.SELL_NOT_ABOVE_BUY : null;
    }

    static boolean outOfRange(double value) {
        return value < 0 || value > MAXIMUM;
    }
}
