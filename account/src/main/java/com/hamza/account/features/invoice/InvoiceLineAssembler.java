package com.hamza.account.features.invoice;

import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.service.ItemUnits;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Converts editable invoice rows into the detached lines sent to the DAO. */
public final class InvoiceLineAssembler {

    private InvoiceLineAssembler() {
    }

    public static <T extends BasePurchasesAndSales> List<T> assemble(
            List<? extends BasePurchasesAndSales> source, int documentId, LineFactory<T> factory) throws DaoException {
        if (source == null || source.isEmpty()) {
            throw new UserValidationException(text("invoice.line.error.no.lines"));
        }

        List<T> result = new ArrayList<>(source.size());
        for (BasePurchasesAndSales row : source) {
            if (row == null || row.getItems() == null || row.getUnitsType() == null) {
                throw new UserValidationException(text("invoice.line.error.incomplete"));
            }
            ItemsModel item = row.getItems();
            if (item.isHasValidate() && row.getExpiration_date() == null) {
                throw new UserValidationException(text("invoice.line.error.expiry.required", item.getNameItem()));
            }

            T detached = factory.create(
                    row.getId(), documentId, item.getId(), row.getPrice(), row.getQuantity(),
                    row.getDiscount(), row.getTotal(), storableUnit(row.getUnitsType()), item,
                    row.getExpiration_date());
            preserveHistoricalCost(row, detached);
            preserveSourceLine(row, detached);
            preserveForeignFigures(row, detached);
            preserveListPrice(row, detached);
            preserveOffer(row, detached);
            result.add(detached);
        }
        return List.copyOf(result);
    }

    private static String text(String key, Object... args) {
        return LanguageManager.getInstance().getString(key, args);
    }

    /**
     * Carries the price tier's list price behind a line (V84) onto the detached row, for the reason
     * {@link #preserveSourceLine} carries the source line: {@link LineFactory} is the seam all four
     * families share, and only a sales line stores one.
     */
    static void preserveListPrice(BasePurchasesAndSales source, BasePurchasesAndSales target) {
        target.setListPrice(source.getListPrice());
        target.setFromFirstTier(source.isFromFirstTier());
    }

    /**
     * Carries the offer behind a line's discount (V85) onto the detached row, for the same reason: on a sale
     * {@code OfferGuard} has already held it to what the engine gives, and on a return
     * {@code ReturnCostResolver} sets it again from the source line.
     */
    static void preserveOffer(BasePurchasesAndSales source, BasePurchasesAndSales target) {
        target.setOfferId(source.getOfferId());
        target.setOfferDiscount(source.getOfferDiscount());
        target.setOfferQuantity(source.getOfferQuantity());
        target.setOfferName(source.getOfferName());
    }

    /**
     * The unit as it is safe to store. The four DAOs write {@code type_value} from
     * {@code getUnitsType().getValue()} and {@code quantity_items_table} computes a
     * balance as {@code quantity * type_value}, so a factor of zero - which a
     * {@link UnitsModel} built by hand carries - would persist a line that moves no
     * stock at all. {@link ItemUnits#factor} is the one place that guards it; this is
     * the last point every persisted line passes through, so it is guarded here rather
     * than in each of the four DAOs.
     */
    private static UnitsModel storableUnit(UnitsModel unit) {
        double factor = ItemUnits.factor(unit);
        if (unit.getValue() == factor) {
            return unit;
        }
        return new UnitsModel(unit.getUnit_id(), unit.getUnit_name(), factor);
    }

    static void preserveHistoricalCost(BasePurchasesAndSales source,
                                       BasePurchasesAndSales target) {
        if (source.getId() > 0) {
            target.setBuy_price(source.getBuy_price());
        }
    }

    /**
     * Carries which sold/purchased line a return line reverses onto the detached row.
     * <p>
     * {@link LineFactory} has no parameter for it and deliberately gains none - it is
     * the seam all four document families share, and only the two return families have
     * a source line. So the value is copied across afterwards, exactly as
     * {@link #preserveHistoricalCost} copies the cost for the same reason.
     * <p>
     * Without this the whole link was inert end to end: the picker set it on the table
     * row, {@code ReturnCostResolver} read it from that row and so still corrected the
     * cost, but the row the DAO actually wrote was this detached one - and it carried
     * {@code 0}, so every {@code source_line_id} in the database was NULL and no saved
     * return could be checked when reopened.
     */
    static void preserveSourceLine(BasePurchasesAndSales source,
                                   BasePurchasesAndSales target) {
        target.setSourceLineId(source.getSourceLineId());
    }

    /**
     * Carries a line's price and discount as typed in the document's currency (V83) onto the detached
     * row, for the reason {@link #preserveSourceLine} carries the source line: {@link LineFactory} is
     * the seam all four families share, and only a document in a foreign currency has these.
     */
    static void preserveForeignFigures(BasePurchasesAndSales source,
                                       BasePurchasesAndSales target) {
        target.setPriceForeign(source.getPriceForeign());
        target.setDiscountForeign(source.getDiscountForeign());
    }

    @FunctionalInterface
    public interface LineFactory<T extends BasePurchasesAndSales> {
        T create(int id, int documentId, int itemId, double price, double quantity,
                 double discount, double total, UnitsModel unit, ItemsModel item,
                 LocalDate expirationDate);
    }
}
