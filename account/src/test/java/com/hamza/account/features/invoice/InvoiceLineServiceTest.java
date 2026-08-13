package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.interfaces.impl_invoiceBuy.PurchaseInvoice;
import com.hamza.account.interfaces.impl_invoiceBuy.SalesInvoice;
import com.hamza.account.interfaces.impl_invoiceBuy.SalesInvoiceReturn;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Purchase;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.Sales_Return;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceLineServiceTest {

    @Test
    void mergesOnlyTheSameItemAndUnitAndKeepsTheStoredIdentity() throws Exception {
        var service = salesService();
        var lines = new ArrayList<Sales>();
        ItemsModel item = item(100);
        UnitsModel piece = unit(1, 1);
        UnitsModel carton = unit(2, 12);

        Sales retained = new SalesInvoice().object_TableData(
                37, 91, item.getId(), 10, 1, 0, 10, piece, item, null);
        lines.add(retained);

        var merged = service.add(lines, draft(item, piece, 2, 10), true, false);
        var separateUnit = service.add(lines, draft(item, carton, 1, 120), true, false);

        assertFalse(merged.inserted());
        assertEquals(37, merged.line().getId());
        assertEquals(3, merged.line().getQuantity());
        assertEquals(30, merged.line().getTotal());
        assertTrue(separateUnit.inserted());
        assertEquals(2, lines.size());
    }

    @Test
    void calculatesLineMoneyInsideTheService() throws Exception {
        var service = salesService();
        var lines = new ArrayList<Sales>();
        ItemsModel item = item(10);
        item.setBuyPrice(0);

        Sales line = service.add(lines,
                draft(item, unit(1, 1), 0.3, 0.1), false, true).line();

        assertEquals(0.03, line.getTotal());
        assertEquals(0.03, line.getTotal_after_discount());
    }

    @Test
    void salesRejectBelowCostAndInsufficientStock() {
        var service = salesService();
        ItemsModel item = item(2);
        UnitsModel piece = unit(1, 1);

        assertThrows(BusinessRuleException.class,
                () -> service.add(new ArrayList<>(), draft(item, piece, 1, 4), false, false));
        assertThrows(BusinessRuleException.class,
                () -> service.add(new ArrayList<>(), draft(item, piece, 3, 10), false, false));
    }

    @Test
    void returnsDoNotApplyRulesThatBelongOnlyToSales() throws Exception {
        ItemsModel item = item(0);
        UnitsModel piece = unit(1, 1);
        var service = new InvoiceLineService<Sales_Return>(DocumentType.SALES_RETURN, 0,
                new SalesInvoiceReturn()::object_TableData);

        var result = service.add(new ArrayList<>(),
                draft(item, piece, 5, 1), false, false);

        assertTrue(result.inserted());
    }

    @Test
    void purchasesAreNotRejectedBySalesStockRules() throws Exception {
        ItemsModel item = item(0);
        var service = new InvoiceLineService<Purchase>(DocumentType.PURCHASE, 0,
                new PurchaseInvoice()::object_TableData);

        var result = service.add(new ArrayList<>(),
                draft(item, unit(1, 1), 5, 1), false, false);

        assertTrue(result.inserted());
    }

    @Test
    void editingSalesInvoiceRestoresItsOriginalStockBeforeCheckingNewQuantity() throws Exception {
        ItemsModel item = item(2);
        UnitsModel piece = unit(1, 1);
        var service = salesService();
        Sales original = new SalesInvoice().object_TableData(
                37, 91, item.getId(), 10, 10, 0, 100, piece, item, null);
        service.captureOriginalLines(java.util.List.of(original));

        var lines = new ArrayList<Sales>();
        lines.add(original);
        service.add(lines, draft(item, piece, 2, 10), true, false);

        assertEquals(12, original.getQuantity());
        assertThrows(BusinessRuleException.class,
                () -> service.add(lines, draft(item, piece, 1, 10), true, false));
    }

    @Test
    void saveValidationChecksQuantitiesChangedDirectlyInsideTheTable() throws Exception {
        ItemsModel item = item(3);
        UnitsModel piece = unit(1, 1);
        var service = salesService();
        Sales line = new SalesInvoice().object_TableData(
                37, 91, item.getId(), 10, 2, 0, 20, piece, item, null);
        service.captureOriginalLines(java.util.List.of(line));

        line.setQuantity(5);
        service.validateForSave(java.util.List.of(line), false);

        line.setQuantity(6);
        assertThrows(BusinessRuleException.class,
                () -> service.validateForSave(java.util.List.of(line), false));
    }

    @Test
    void saveValidationChecksPricesChangedDirectlyInsideTheTable() {
        ItemsModel item = item(20);
        UnitsModel piece = unit(1, 1);
        var service = salesService();
        Sales line = new SalesInvoice().object_TableData(
                37, 91, item.getId(), 4, 1, 0, 4, piece, item, null);

        assertThrows(BusinessRuleException.class,
                () -> service.validateForSave(java.util.List.of(line), false));
    }

    private static InvoiceLineService<Sales> salesService() {
        return new InvoiceLineService<>(DocumentType.SALES, 91,
                new SalesInvoice()::object_TableData);
    }

    private static InvoiceLineDraft draft(ItemsModel item, UnitsModel unit,
                                          double quantity, double price) {
        return new InvoiceLineDraft(item, unit, quantity, price, 0, null);
    }

    private static ItemsModel item(double balance) {
        ItemsModel item = new ItemsModel(12, "B12", "صنف اختبار");
        item.setBuyPrice(5);
        item.setSumAllBalance(balance);
        return item;
    }

    private static UnitsModel unit(int id, double factor) {
        return new UnitsModel(id, "وحدة " + id, factor);
    }
}
