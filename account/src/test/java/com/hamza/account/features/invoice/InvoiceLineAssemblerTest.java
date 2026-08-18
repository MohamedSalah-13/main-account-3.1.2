package com.hamza.account.features.invoice;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.database.DaoException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvoiceLineAssemblerTest {

    @Test
    void preservesIdentityDocumentAndHistoricalCostForAnExistingLine() throws DaoException {
        Sales source = line(17, false);
        source.setBuy_price(4.25);

        Sales detached = InvoiceLineAssembler.assemble(
                List.of(source), 700, InvoiceLineAssemblerTest::copyWithCurrentCost).getFirst();

        assertEquals(17, detached.getId());
        assertEquals(700, detached.getInvoiceNumber());
        assertEquals(4.25, detached.getBuy_price());
    }

    @Test
    void aNewLineKeepsTheCurrentCostCalculatedByItsStrategy() throws DaoException {
        Sales detached = InvoiceLineAssembler.assemble(
                List.of(line(0, false)), 700, InvoiceLineAssemblerTest::copyWithCurrentCost).getFirst();

        assertEquals(0, detached.getId());
        assertEquals(99, detached.getBuy_price());
    }

    @Test
    void rejectsAValidityTrackedItemWithoutAnExpirationDate() {
        assertThrows(DaoException.class, () -> InvoiceLineAssembler.assemble(
                List.of(line(0, true)), 700, InvoiceLineAssemblerTest::copyWithCurrentCost));
    }

    @Test
    void carriesTheSourceLineOntoTheDetachedRow() throws DaoException {
        // LineFactory has no parameter for it, so without an explicit copy the value the
        // picker sets on the table row never reaches the row the DAO writes - which is
        // how every source_line_id in the database came out NULL.
        Sales source = line(17, false);
        source.setSourceLineId(501);

        Sales detached = InvoiceLineAssembler.assemble(
                List.of(source), 700, InvoiceLineAssemblerTest::copyWithCurrentCost).getFirst();

        assertEquals(501, detached.getSourceLineId());
    }

    @Test
    void aLineWithNoSourceStaysAtZero() throws DaoException {
        Sales detached = InvoiceLineAssembler.assemble(
                List.of(line(0, false)), 700, InvoiceLineAssemblerTest::copyWithCurrentCost).getFirst();

        assertEquals(0, detached.getSourceLineId());
    }

    private static Sales line(int id, boolean validity) {
        ItemsModel item = new ItemsModel(12, "B12", "صنف اختبار");
        item.setHasValidate(validity);
        Sales line = new Sales();
        line.setId(id);
        line.setItems(item);
        line.setUnitsType(new UnitsModel(1, "قطعة", 1));
        line.setQuantity(2);
        line.setPrice(10);
        line.setDiscount(1);
        line.setTotal(20);
        return line;
    }

    private static Sales copyWithCurrentCost(int id, int documentId, int itemId,
                                             double price, double quantity, double discount,
                                             double total, UnitsModel unit, ItemsModel item,
                                             LocalDate expirationDate) {
        Sales copy = new Sales();
        copy.setId(id);
        copy.setInvoiceNumber(documentId);
        copy.setNumItem(itemId);
        copy.setPrice(price);
        copy.setQuantity(quantity);
        copy.setDiscount(discount);
        copy.setTotal(total);
        copy.setUnitsType(unit);
        copy.setItems(item);
        copy.setExpiration_date(expirationDate);
        copy.setBuy_price(99);
        return copy;
    }
}
