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

    @Test
    void aBundlesComponentComesInTheUnitItNames() throws Exception {
        ItemsModel item = item();
        com.hamza.account.model.domain.ItemsUnitsModel base = new com.hamza.account.model.domain.ItemsUnitsModel();
        base.setUnitsModel(new UnitsModel(1, "piece", 1));
        base.setQuantityForUnit(1);
        com.hamza.account.model.domain.ItemsUnitsModel carton = new com.hamza.account.model.domain.ItemsUnitsModel();
        carton.setUnitsModel(new UnitsModel(2, "carton", 12));
        carton.setQuantityForUnit(12);
        carton.setSelPrice(100);
        item.setItemsUnitsModelList(new java.util.ArrayList<>(java.util.List.of(base, carton)));
        var service = new InvoiceItemPickerService(
                DocumentType.SALES, (itemId, stockId) -> item, (model, tier) -> model.getSelPrice1());

        InvoiceLineDraft cartons = service.resolve(new ItemPickRequest(7, "", 2, 2), 1, 1).orElseThrow();
        assertEquals(2, cartons.unit().getUnit_id());
        assertEquals(100, cartons.price(), "the carton's own price");
        assertEquals(2, cartons.quantity());

        assertEquals(1, service.resolve(new ItemPickRequest(7, "", 1), 1, 1).orElseThrow().unit().getUnit_id(),
                "no unit named is the base");
        assertTrue(service.resolve(new ItemPickRequest(7, "", 1, 9), 1, 1).isEmpty(),
                "a unit the item no longer has is not turned into a line");
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
