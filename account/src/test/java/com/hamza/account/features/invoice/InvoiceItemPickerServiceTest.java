package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceItemPickerServiceTest {

    @Test
    void reloadsTheChosenIdInTheInvoicesCurrentWarehouse() throws Exception {
        AtomicInteger askedItem = new AtomicInteger();
        AtomicInteger askedStock = new AtomicInteger();
        ItemsModel item = item();
        var service = new InvoiceItemPickerService(
                DocumentType.SALES,
                (itemId, stockId) -> {
                    askedItem.set(itemId);
                    askedStock.set(stockId);
                    return item;
                },
                (model, tier) -> model.getSelPrice2());

        InvoiceLineDraft draft = service.resolve(
                new ItemPickRequest(7, "catalog name", 1), 4, 2).orElseThrow();

        assertEquals(7, askedItem.get());
        assertEquals(4, askedStock.get());
        assertEquals(item, draft.item());
        assertEquals(9, draft.price());
        assertEquals(1, draft.quantity());
    }

    @Test
    void purchaseDocumentsUseCostEvenWhenThePriceResolverReturnsASalesTier() throws Exception {
        ItemsModel item = item();
        var service = new InvoiceItemPickerService(
                DocumentType.PURCHASE, (itemId, stockId) -> item, (model, tier) -> 99);

        InvoiceLineDraft draft = service.resolve(
                new ItemPickRequest(7, "item", 1), 2, 3).orElseThrow();

        assertEquals(7.5, draft.price());
    }

    @Test
    void aChoiceNoLongerAvailableInTheWarehouseIsNotTurnedIntoALine() throws Exception {
        var service = new InvoiceItemPickerService(
                DocumentType.SALES, (itemId, stockId) -> null, (model, tier) -> 10);

        assertTrue(service.resolve(new ItemPickRequest(7, "item", 1), 4, 1).isEmpty());
    }

    private static ItemsModel item() {
        ItemsModel item = new ItemsModel(7, "BASE-7", "item");
        item.setUnitsType(new UnitsModel(1, "piece", 1));
        item.setBuyPrice(7.5);
        item.setSelPrice1(10);
        item.setSelPrice2(9);
        return item;
    }
}
