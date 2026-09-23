package com.hamza.account.features.invoice;

import com.hamza.account.features.returns.ReturnableRepository;
import com.hamza.account.interfaces.impl_invoiceBuy.SalesInvoice;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A document's lines as typed in its currency, turned into the base lines stored for them
 * (docs/currency-plan.md §15 ق-د٣ and ق-د٥).
 */
class ForeignDocumentLinesTest {

    private static final BigDecimal RATE = new BigDecimal("48.37");

    @Test
    @DisplayName("a line is stored in the base at the rate, with what was typed beside it")
    void aLineConverts() throws Exception {
        Sales typed = line(0, 2.07, 3, 0.10);

        Sales base = ForeignDocumentLines.toBase(List.of(typed), RATE, null, new SalesInvoice()::object_TableData)
                .getFirst();

        assertEquals(100.13, base.getPrice(), 0.0001, "2.07 x 48.37 = 100.1259");
        assertEquals(4.84, base.getDiscount(), 0.0001, "0.10 x 48.37 = 4.837");
        assertEquals(300.39, base.getTotal(), 0.0001, "the base price times the quantity, as any line's total");
        assertEquals(new BigDecimal("2.07"), base.getPriceForeign());
        assertEquals(new BigDecimal("0.10"), base.getDiscountForeign());
        assertEquals(3, base.getQuantity(), 0.0001);
        assertNotSame(typed, base, "a copy - the typed row is the screen's");
        assertEquals(2.07, typed.getPrice(), 0.0001, "and the screen's row is left as it was");
    }

    @Test
    @DisplayName("a return line picked from its invoice takes that line's own base price and discount share")
    void aPickedReturnLineTakesTheSourcesBaseFigures() throws Exception {
        // The invoice sold 10 at 100.00 with 4.85 off the line, at 48.37; the return takes 4 of them.
        var source = new ReturnableRepository.SourceLine(12, 10, 100.00, 4.85, 60, 1, 1, null);
        Sales typed = line(0, 2.07, 4, 0.04);
        typed.setSourceLineId(900);

        Sales base = ForeignDocumentLines.toBase(List.of(typed), RATE,
                id -> id == 900 ? Optional.of(source) : Optional.empty(),
                new SalesInvoice()::object_TableData).getFirst();

        assertEquals(100.00, base.getPrice(), 0.0001, "not 2.07 x 48.37 = 100.13");
        assertEquals(1.94, base.getDiscount(), 0.0001, "four tenths of 4.85 - not 0.04 x 48.37");
        assertEquals(900, base.getSourceLineId());
        assertEquals(new BigDecimal("2.07"), base.getPriceForeign(), "the typed figures are still written");
    }

    @Test
    @DisplayName("an existing line keeps the cost it was sold at, as the base save does")
    void anExistingLineKeepsItsCost() throws Exception {
        Sales typed = line(33, 2.07, 1, 0);
        typed.setBuy_price(61.50);

        Sales base = ForeignDocumentLines.toBase(List.of(typed), RATE, null, new SalesInvoice()::object_TableData)
                .getFirst();

        assertEquals(33, base.getId());
        assertEquals(61.50, base.getBuy_price(), 0.0001);
    }

    @Test
    @DisplayName("a line with no item or unit is refused, as the assembler refuses it")
    void anIncompleteLineIsRefused() {
        Sales typed = line(0, 1, 1, 0);
        typed.setUnitsType(null);
        assertThrows(UserValidationException.class, () -> ForeignDocumentLines.toBase(List.of(typed), RATE, null,
                new SalesInvoice()::object_TableData));
    }

    @Test
    @DisplayName("the assembler carries the typed figures onto the rows it hands the DAO")
    void theAssemblerCarriesThem() throws Exception {
        Sales base = ForeignDocumentLines.toBase(List.of(line(0, 2.07, 3, 0.10)), RATE, null,
                new SalesInvoice()::object_TableData).getFirst();

        Sales persisted = InvoiceLineAssembler.assemble(List.of(base), 46, new SalesInvoice()::object_TableData)
                .getFirst();

        assertEquals(100.13, persisted.getPrice(), 0.0001);
        assertEquals(new BigDecimal("2.07"), persisted.getPriceForeign());
        assertEquals(new BigDecimal("0.10"), persisted.getDiscountForeign());
    }

    private static Sales line(int id, double price, double quantity, double discount) {
        ItemsModel item = new ItemsModel();
        item.setId(12);
        item.setNameItem("صنف");
        item.setBuyPrice(40);
        Sales line = new Sales();
        line.setId(id);
        line.setItems(item);
        line.setUnitsType(new UnitsModel(1, "قطعة", 1));
        line.setPrice(price);
        line.setQuantity(quantity);
        line.setDiscount(discount);
        line.setTotal(price * quantity);
        return line;
    }
}
