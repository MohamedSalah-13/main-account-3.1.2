package com.hamza.account.features.invoice;

import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Converts editable invoice rows into the detached lines sent to the DAO. */
public final class InvoiceLineAssembler {

    private InvoiceLineAssembler() {
    }

    public static <T extends BasePurchasesAndSales> List<T> assemble(
            List<T> source, int documentId, LineFactory<T> factory) throws DaoException {
        if (source == null || source.isEmpty()) {
            throw new UserValidationException("لا يمكن حفظ فاتورة بدون أصناف");
        }

        List<T> result = new ArrayList<>(source.size());
        for (T row : source) {
            if (row == null || row.getItems() == null || row.getUnitsType() == null) {
                throw new UserValidationException("بيانات أحد سطور الفاتورة غير مكتملة");
            }
            ItemsModel item = row.getItems();
            if (item.isHasValidate() && row.getExpiration_date() == null) {
                throw new UserValidationException("يجب تحديد تاريخ صلاحية الصنف: " + item.getNameItem());
            }

            T detached = factory.create(
                    row.getId(), documentId, item.getId(), row.getPrice(), row.getQuantity(),
                    row.getDiscount(), row.getTotal(), row.getUnitsType(), item,
                    row.getExpiration_date());
            preserveHistoricalCost(row, detached);
            preserveSourceLine(row, detached);
            result.add(detached);
        }
        return List.copyOf(result);
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

    @FunctionalInterface
    public interface LineFactory<T extends BasePurchasesAndSales> {
        T create(int id, int documentId, int itemId, double price, double quantity,
                 double discount, double total, UnitsModel unit, ItemsModel item,
                 LocalDate expirationDate);
    }
}
