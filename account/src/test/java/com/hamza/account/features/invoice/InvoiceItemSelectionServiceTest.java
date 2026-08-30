package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.ItemsUnitsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.otherSetting.BarcodeResult;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceItemSelectionServiceTest {

    private static final UnitsModel PIECE = new UnitsModel(1, "قطعة", 1);
    private static final UnitsModel CARTON = new UnitsModel(2, "كرتونة", 12);

    @Test
    void nameSearchStartsFromTheBaseUnitAndRequestedPriceTier() throws Exception {
        ItemsModel item = item();
        var service = service(DocumentType.SALES, item, (model, tier) -> model.getSelPrice2());

        InvoiceItemSelection selection = service.selectByName("صنف اختبار", 1, 2);

        assertEquals(PIECE.getUnit_id(), selection.selectedUnit().getUnit_id());
        assertEquals(9, selection.price());
        assertEquals(1, selection.quantity());
        assertEquals(9, selection.total());
        assertEquals(24, selection.balance());
        assertFalse(selection.scaleBarcode());
    }

    @Test
    void unitBarcodeSelectsThatUnitAndItsSalesPrice() throws Exception {
        ItemsModel item = item();

        for (DocumentType type : List.of(DocumentType.SALES, DocumentType.SALES_RETURN)) {
            var service = service(type, item, (model, tier) -> model.getSelPrice1());
            InvoiceItemSelection selection = service.selectByBarcode(
                    "CARTON-12", 1, 1,
                    InvoiceItemSelectionService.ScaleBarcodeSettings.disabled());

            assertEquals(CARTON.getUnit_id(), selection.selectedUnit().getUnit_id());
            assertEquals(100, selection.price());
            assertEquals(2, selection.balance());
        }
    }

    @Test
    void purchaseAndPurchaseReturnUseTheUnitsBuyPrice() throws Exception {
        ItemsModel item = item();

        for (DocumentType type : List.of(DocumentType.PURCHASE, DocumentType.PURCHASE_RETURN)) {
            var service = service(type, item, (model, tier) -> model.getBuyPrice());
            InvoiceItemSelection selection = service.selectByBarcode(
                    "CARTON-12", 1, 1,
                    InvoiceItemSelectionService.ScaleBarcodeSettings.disabled());

            assertEquals(90, selection.price());
        }
    }

    @Test
    void scaleBarcodeUsesResolvedInvoicePriceAndWeight() throws Exception {
        ItemsModel item = item();
        var lookup = lookup(item);
        var service = new InvoiceItemSelectionService(
                DocumentType.SALES, lookup, (model, tier) -> model.getSelPrice2(),
                (barcode, stockId, valueType) -> new BarcodeResult(item, 999, 999, 0.375));

        InvoiceItemSelection selection = service.selectByBarcode(
                "2700001003751", 1, 2,
                new InvoiceItemSelectionService.ScaleBarcodeSettings(true, 27, 2));

        assertTrue(selection.scaleBarcode());
        assertEquals(PIECE.getUnit_id(), selection.selectedUnit().getUnit_id());
        assertEquals(9, selection.price());
        assertEquals(0.375, selection.quantity());
        assertEquals(3.38, selection.total());
    }

    @Test
    void missingItemProducesUserFacingValidation() {
        var service = new InvoiceItemSelectionService(
                DocumentType.SALES, lookup(null), (model, tier) -> 10,
                (barcode, stockId, valueType) -> null);

        assertThrows(UserValidationException.class,
                () -> service.selectByName("غير موجود", 1, 1));
        assertThrows(UserValidationException.class,
                () -> service.selectByBarcode("404", 1, 1,
                        InvoiceItemSelectionService.ScaleBarcodeSettings.disabled()));
    }

    @Test
    void scalePrefixMatchingIsExplicitAndSafeForShortCodes() {
        var settings = new InvoiceItemSelectionService.ScaleBarcodeSettings(true, 7, 2);

        assertTrue(settings.matches("070001"));
        assertFalse(settings.matches("7"));
        assertFalse(settings.matches("270001"));
        assertFalse(InvoiceItemSelectionService.ScaleBarcodeSettings.disabled().matches("070001"));
    }

    private static InvoiceItemSelectionService service(
            DocumentType type, ItemsModel item,
            InvoiceItemSelectionService.ItemPriceResolver resolver) {
        return new InvoiceItemSelectionService(type, lookup(item), resolver,
                (barcode, stockId, valueType) -> new BarcodeResult(item, item.getSelPrice1(), item.getSelPrice1(), 1));
    }

    private static InvoiceItemSelectionService.ItemLookup lookup(ItemsModel item) {
        return new InvoiceItemSelectionService.ItemLookup() {
            @Override
            public ItemsModel byName(String name, int stockId) {
                return item;
            }

            @Override
            public ItemsModel byBarcode(String barcode, int stockId) {
                return item;
            }
        };
    }

    private static ItemsModel item() {
        ItemsModel item = new ItemsModel(7, "BASE-7", "صنف اختبار");
        item.setUnitsType(PIECE);
        item.setBuyPrice(7.5);
        item.setSelPrice1(10);
        item.setSelPrice2(9);
        item.setSumAllBalance(24);

        ItemsUnitsModel base = new ItemsUnitsModel();
        base.setUnitsModel(PIECE);
        base.setQuantityForUnit(1);
        base.setItemsBarcode("BASE-7");

        ItemsUnitsModel carton = new ItemsUnitsModel();
        carton.setUnitsModel(CARTON);
        carton.setQuantityForUnit(12);
        carton.setItemsBarcode("CARTON-12");
        carton.setBuyPrice(90);
        carton.setSelPrice(100);
        carton.setSelPrice2(95);

        item.setItemsUnitsModelList(new ArrayList<>(List.of(base, carton)));
        return item;
    }
}
