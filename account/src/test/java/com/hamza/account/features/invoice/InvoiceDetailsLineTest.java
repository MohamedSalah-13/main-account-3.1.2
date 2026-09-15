package com.hamza.account.features.invoice;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.UnitsModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvoiceDetailsLineTest {

    @Test
    void buildsAnImmutableDisplayLineAndCalculatesItsNet() {
        Sales source = new Sales();
        source.setItems(new ItemsModel(17, "Coffee"));
        source.setUnitsType(new UnitsModel(2, "Box", 12));
        source.setQuantity(2.5);
        source.setPrice(40);
        source.setTotal(100);
        source.setDiscount(7.5);

        InvoiceDetailsLine line = InvoiceDetailsLine.from(source);

        assertEquals(17, line.itemId());
        assertEquals("Coffee", line.itemName());
        assertEquals("Box", line.unitName());
        assertDecimal("2.5", line.quantity());
        assertDecimal("40", line.price());
        assertDecimal("92.5", line.net());

        source.getItems().setNameItem("Changed later");
        assertEquals("Coffee", line.itemName());
    }

    @Test
    void toleratesMissingResolvedItemAndUnitData() {
        Sales source = new Sales();
        source.setNumItem(9);

        InvoiceDetailsLine line = InvoiceDetailsLine.from(source);

        assertEquals(9, line.itemId());
        assertEquals("", line.itemName());
        assertEquals("", line.unitName());
        assertDecimal("0", line.net());
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
