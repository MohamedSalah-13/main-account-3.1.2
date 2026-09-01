package com.hamza.account.interfaces.impl_invoiceBuy;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.ItemsUnitsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.UnitsModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a sold line records as its cost, which is the only input to the profit the
 * owner reads: {@code document_profit} is computed from {@code buy_price}.
 *
 * <p>It used to be {@code items.buy_price * type_value} - the piece cost times the
 * factor - which contradicted the floor the same sale is validated against
 * ({@code InvoiceLineService.requireSalePrice} asks {@code ItemUnits.buyPrice}). A unit
 * bought at its own price, which is the whole point of buying by the carton, was
 * therefore costed at a figure the business never paid.
 */
class SalesLineCostTest {

    private static final UnitsModel PIECE = new UnitsModel(1, "قطعة", 1);
    private static final UnitsModel CARTON = new UnitsModel(2, "كرتونة", 12);

    @Test
    void costsAUnitWithNoPriceOfItsOwnAtTheItemsCostTimesTheFactor() {
        Sales line = sell(item(9, null), CARTON, 150, 1);

        assertEquals(108, line.getBuy_price());
    }

    @Test
    void costsAUnitBoughtAtItsOwnPriceAtThatPrice() {
        // Twelve pieces cost 108 one at a time; the carton was bought for 100.
        Sales line = sell(item(9, 100.0), CARTON, 150, 1);

        assertEquals(100, line.getBuy_price());
    }

    @Test
    void theBaseUnitIsUnaffected() {
        Sales line = sell(item(9, 100.0), PIECE, 12, 1);

        assertEquals(9, line.getBuy_price());
    }

    /**
     * A {@link UnitsModel} built by hand carries a factor of zero. Multiplying by it
     * recorded a cost of nothing and booked the entire sale as profit.
     */
    @Test
    void aUnitWithNoFactorCostsOneOfTheItemRatherThanNothing() {
        Sales line = sell(item(9, null), new UnitsModel(3, "لفة", 0), 20, 1);

        assertEquals(9, line.getBuy_price());
    }

    @Test
    void theProfitOfTheDocumentFollowsTheRecordedCost() throws Exception {
        SalesInvoice invoice = new SalesInvoice();
        Sales line = sell(item(9, 100.0), CARTON, 150, 2);

        invoice.object_Totals(1, null, "2026-09-01", 300, 0, null, 300, 300, 0, null,
                null, null, null, List.of(line), null);

        assertEquals(200, line.getTotal_buy_price());
        assertEquals(100, line.getTotal_profit().doubleValue());
    }

    private static Sales sell(ItemsModel item, UnitsModel unit, double price, double quantity) {
        return new SalesInvoice().object_TableData(0, 0, item.getId(), price, quantity, 0,
                price * quantity, unit, item, null);
    }

    /** An item whose carton either carries a purchase price of its own, or does not. */
    private static ItemsModel item(double buyPrice, Double cartonBuyPrice) {
        ItemsModel item = new ItemsModel();
        item.setId(7);
        item.setBuyPrice(buyPrice);
        item.setUnitsType(PIECE);

        ItemsUnitsModel base = new ItemsUnitsModel();
        base.setUnitsModel(PIECE);
        base.setQuantityForUnit(1);

        ItemsUnitsModel carton = new ItemsUnitsModel();
        carton.setUnitsModel(CARTON);
        carton.setQuantityForUnit(12);
        if (cartonBuyPrice != null) {
            carton.setBuyPrice(cartonBuyPrice);
        }

        item.setItemsUnitsModelList(List.of(base, carton));
        return item;
    }
}
