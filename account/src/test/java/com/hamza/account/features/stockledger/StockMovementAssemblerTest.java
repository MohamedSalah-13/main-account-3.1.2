package com.hamza.account.features.stockledger;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.stockcount.StockCount;
import com.hamza.account.features.stockcount.StockCountLine;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Purchase;
import com.hamza.account.model.domain.Purchase_Return;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.Sales_Return;
import com.hamza.account.model.domain.UnitsModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the dual-write half of an invoice save (see
 * {@code docs/erp-roadmap.md} §8.3-8.4): the direction each document type moves stock,
 * and that a line's quantity/unit/factor land on the movement unchanged from what the
 * invoice line itself carries.
 */
class StockMovementAssemblerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 17);
    private static final int STOCK_ID = 1;
    private static final int USER_ID = 9;

    @Nested
    @DisplayName("direction per document type")
    class Direction {

        @Test
        @DisplayName("a sale moves stock out")
        void saleMovesStockOut() {
            List<StockMovement> movements = StockMovementAssembler.assemble(
                    DocumentType.SALES, STOCK_ID, 44, DATE, List.of(sale(3)), USER_ID);

            StockMovement movement = movements.getFirst();
            assertEquals(0, movement.quantityIn());
            assertEquals(3, movement.quantityOut());
            assertEquals(MovementType.SALE, movement.movementType());
            assertEquals("SALE", movement.referenceType());
        }

        @Test
        @DisplayName("a purchase moves stock in")
        void purchaseMovesStockIn() {
            List<StockMovement> movements = StockMovementAssembler.assemble(
                    DocumentType.PURCHASE, STOCK_ID, 55, DATE, List.of(purchase(5)), USER_ID);

            StockMovement movement = movements.getFirst();
            assertEquals(5, movement.quantityIn());
            assertEquals(0, movement.quantityOut());
            assertEquals(MovementType.PURCHASE, movement.movementType());
        }

        @Test
        @DisplayName("a sales return reverses the sale: stock in")
        void salesReturnMovesStockIn() {
            List<StockMovement> movements = StockMovementAssembler.assemble(
                    DocumentType.SALES_RETURN, STOCK_ID, 66, DATE, List.of(salesReturn(2)), USER_ID);

            StockMovement movement = movements.getFirst();
            assertEquals(2, movement.quantityIn());
            assertEquals(0, movement.quantityOut());
            assertEquals(MovementType.SALE_RETURN, movement.movementType());
        }

        @Test
        @DisplayName("a purchase return reverses the purchase: stock out")
        void purchaseReturnMovesStockOut() {
            List<StockMovement> movements = StockMovementAssembler.assemble(
                    DocumentType.PURCHASE_RETURN, STOCK_ID, 77, DATE, List.of(purchaseReturn(1)), USER_ID);

            StockMovement movement = movements.getFirst();
            assertEquals(0, movement.quantityIn());
            assertEquals(1, movement.quantityOut());
            assertEquals(MovementType.PURCHASE_RETURN, movement.movementType());
        }
    }

    @Test
    @DisplayName("the unit and its factor are copied from the line, not re-derived")
    void copiesTheLinesOwnUnitAndFactor() {
        Sales line = sale(4);
        line.setUnitsType(new UnitsModel(3, "كرتونة", 12));

        StockMovement movement = StockMovementAssembler.assemble(
                DocumentType.SALES, STOCK_ID, 1, DATE, List.of(line), USER_ID).getFirst();

        assertEquals(3, movement.unitId());
        assertEquals(12, movement.unitValue());
    }

    @Test
    @DisplayName("stock count: a posted difference becomes one movement, signed and unit-converted")
    void stockCountLineBecomesOneMovement() {
        StockCount count = new StockCount();
        count.setId(9);
        count.setStockId(STOCK_ID);
        count.setCountDate(DATE);
        count.setUserId(USER_ID);
        // Found more than the books said: +12 base units, counted in a unit whose
        // factor is 4 - so the movement itself should read 3, not 12.
        count.setLines(List.of(new StockCountLine(0, 21, "صنف", "123", 5, "كرتونة", 4, 8, 5)));

        List<StockMovement> movements = StockMovementAssembler.forStockCount(count);

        assertEquals(1, movements.size());
        StockMovement movement = movements.getFirst();
        assertEquals(MovementType.INVENTORY_ADJUST_IN, movement.movementType());
        assertEquals(3, movement.quantityIn());
        assertEquals(0, movement.quantityOut());
        assertEquals(4, movement.unitValue());
        assertEquals("INVENTORY", movement.referenceType());
        assertEquals(9, movement.referenceId());
    }

    @Test
    @DisplayName("stock count: a line with no difference produces no movement")
    void stockCountLineWithNoDifferenceIsSkipped() {
        StockCount count = new StockCount();
        count.setId(9);
        count.setStockId(STOCK_ID);
        count.setCountDate(DATE);
        count.setUserId(USER_ID);
        count.setLines(List.of(new StockCountLine(0, 21, "صنف", "123", 1, "قطعة", 1, 10, 10)));

        assertTrue(StockMovementAssembler.forStockCount(count).isEmpty());
    }

    private Sales sale(double quantity) {
        Sales line = new Sales();
        line.setItems(item());
        line.setUnitsType(new UnitsModel(1, "قطعة", 1));
        line.setQuantity(quantity);
        return line;
    }

    private Purchase purchase(double quantity) {
        Purchase line = new Purchase();
        line.setItems(item());
        line.setUnitsType(new UnitsModel(1, "قطعة", 1));
        line.setQuantity(quantity);
        return line;
    }

    private Sales_Return salesReturn(double quantity) {
        Sales_Return line = new Sales_Return();
        line.setItems(item());
        line.setUnitsType(new UnitsModel(1, "قطعة", 1));
        line.setQuantity(quantity);
        return line;
    }

    private Purchase_Return purchaseReturn(double quantity) {
        Purchase_Return line = new Purchase_Return();
        line.setItems(item());
        line.setUnitsType(new UnitsModel(1, "قطعة", 1));
        line.setQuantity(quantity);
        return line;
    }

    private ItemsModel item() {
        ItemsModel item = new ItemsModel();
        item.setId(21);
        return item;
    }
}
