package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.pricing.PriceTiers;
import com.hamza.account.interfaces.impl_invoiceBuy.SalesInvoice;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The price tier on the invoice's own path (V84): a line takes its tier's list price, tier 1's where
 * the tier has none, carries the list price it was offered at, and a price typed under it is refused
 * to whoever may not sell below the list.
 */
class InvoiceTierPricingTest {

    private static final UnitsModel PIECE = new UnitsModel(1, "قطعة", 1);

    private static ItemsModel soap(double retail, double wholesale) {
        ItemsModel item = new ItemsModel(5, "B5", "صابون");
        item.setSelPrice1(retail);
        item.setSelPrice3(wholesale);
        item.setBuyPrice(4);
        item.setSumAllBalance(100);
        item.setUnitsType(PIECE);
        // The base unit is a row of the list, as ItemsDao builds it.
        var base = new com.hamza.account.model.domain.ItemsUnitsModel();
        base.setUnitsModel(PIECE);
        base.setQuantityForUnit(1);
        item.setItemsUnitsModelList(new ArrayList<>(List.of(base)));
        return item;
    }

    private static InvoiceItemSelectionService selection(DocumentType type, ItemsModel item) {
        return new InvoiceItemSelectionService(type, new InvoiceItemSelectionService.ItemLookup() {
            @Override
            public ItemsModel byName(String name, int stockId) {
                return item;
            }

            @Override
            public ItemsModel byBarcode(String barcode, int stockId) {
                return item;
            }
        }, type.side() == com.hamza.account.features.events.InvoiceSide.PURCHASE
                ? (model, tier) -> model.getBuyPrice()
                : PriceTiers::itemPrice,
                (barcode, stockId, valueType) -> null);
    }

    @Test
    @DisplayName("a wholesale customer's line at a wholesale price, with the list price behind it")
    void tierPrice() throws Exception {
        InvoiceItemSelection selected = selection(DocumentType.SALES, soap(10, 9)).selectByName("صابون", 1, 3);
        assertEquals(9, selected.price());
        assertEquals(new InvoiceLineDraft.Listed(9, false), selected.listed());
    }

    @Test
    @DisplayName("no wholesale price: sold at tier 1's and marked - it used to be a zero and a refusal")
    void fallsBackAndMarks() throws Exception {
        InvoiceItemSelection selected = selection(DocumentType.SALES, soap(10, 0)).selectByName("صابون", 1, 3);
        assertEquals(10, selected.price());
        assertTrue(selected.fromFirstTier());

        List<Sales> lines = new ArrayList<>();
        Sales line = new InvoiceLineService<>(DocumentType.SALES, 0, new SalesInvoice()::object_TableData)
                .add(lines, selected.draft(), false, false).line();
        assertEquals(new BigDecimal("10.00"), line.getListPrice());
        assertTrue(line.isFromFirstTier());
    }

    @Test
    @DisplayName("a purchase has no list behind it; a sales return keeps one on screen and stores none")
    void families() throws Exception {
        assertNull(selection(DocumentType.PURCHASE, soap(10, 9)).selectByName("صابون", 1, 1).listed());
        assertEquals(new InvoiceLineDraft.Listed(9, false),
                selection(DocumentType.SALES_RETURN, soap(10, 9)).selectByName("صابون", 1, 3).listed());
    }

    @Test
    @DisplayName("a price typed under the list is refused to whoever may not sell below it")
    void belowListOnAdd() {
        InvoiceLineService<Sales> strict = new InvoiceLineService<>(DocumentType.SALES, 0,
                new SalesInvoice()::object_TableData).undercutAllowedWhen(() -> false);
        InvoiceLineDraft typedUnder = new InvoiceLineDraft(soap(10, 9), PIECE, 1, 8.5, 0, null,
                new InvoiceLineDraft.Listed(9, false));
        assertThrows(BusinessRuleException.class, () -> strict.add(new ArrayList<>(), typedUnder, false, false));
        assertDoesNotThrow(() -> strict.add(new ArrayList<>(), typedUnder.withListed(null), false, false),
                "no list behind the line, nothing to be under");

        InvoiceLineService<Sales> allowed = new InvoiceLineService<>(DocumentType.SALES, 0,
                new SalesInvoice()::object_TableData).undercutAllowedWhen(() -> true);
        assertDoesNotThrow(() -> allowed.add(new ArrayList<>(), typedUnder, false, false));
    }

    @Test
    @DisplayName("the same rule on a price typed in the table, and a new unit restores the list")
    void belowListOnEdit() throws Exception {
        InvoiceLineEditService edits = new InvoiceLineEditService(DocumentType.SALES,
                new InvoiceItemCatalogService(DocumentType.SALES, (com.hamza.account.service.ItemsService) null,
                        (item, price, tier) -> false),
                1).undercutAllowedWhen(() -> false);
        Sales line = new SalesInvoice().object_TableData(0, 0, 5, 9, 1, 0, 9, PIECE, soap(10, 9), null);
        line.setListPrice(new BigDecimal("9.00"));

        assertThrows(BusinessRuleException.class, () -> edits.editPrice(line, 8.0, false, 3));
        edits.editPrice(line, 9.5, false, 3);
        assertEquals(9.5, line.getPrice());

        edits.editUnit(line, new InvoiceItemSelectionService.UnitSelection(PIECE, 9, 100,
                new InvoiceLineDraft.Listed(9, false)));
        assertEquals(new BigDecimal("9.00"), line.getListPrice());
        assertFalse(line.isFromFirstTier());
    }
}
