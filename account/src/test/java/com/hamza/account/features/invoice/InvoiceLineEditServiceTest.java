package com.hamza.account.features.invoice;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.ItemsUnitsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceLineEditServiceTest {

    private static final UnitsModel PIECE = new UnitsModel(1, "قطعة", 1);
    private static final UnitsModel CARTON = new UnitsModel(2, "كرتونة", 12);

    private UserSessionContext session;

    /**
     * Editing a name or a catalogue price from an invoice line writes the item, so it asks
     * for {@code items.update}. Not user 1: that id is the recovery administrator and
     * bypasses every permission, which would make the refusal below unable to fail.
     */
    @BeforeEach
    void signIn() {
        session = new UserSessionContext();
        ServiceRegistry.register(UserSessionContext.class, session);
        session.signIn(7, "cashier", Set.of(AppPermissions.ITEMS_UPDATE, AppPermissions.SALES_CREATE));
    }

    @Test
    void salesRejectBelowCostButSalesReturnsAllowIt() throws Exception {
        Sales sale = line(item(false), PIECE, 10);
        InvoiceLineEditService sales = editService(DocumentType.SALES,
                new TrackingRepository(item(false)), (item, price, tier) -> true);
        InvoiceLineEditService salesReturn = editService(DocumentType.SALES_RETURN,
                new TrackingRepository(item(false)), (item, price, tier) -> false);

        assertThrows(BusinessRuleException.class,
                () -> sales.editPrice(sale, 4.0, false, 1));
        assertEquals(10, sale.getPrice());

        salesReturn.editPrice(sale, 4.0, false, 1);
        assertEquals(4, sale.getPrice());
    }

    /**
     * A discount is at most the line - more left a net below zero (docs/pricing-and-offers-plan.md §1.2) - and
     * a line an offer reached takes no manual one: the offer takes its place (ق-ع٨).
     */
    @Test
    void aDiscountIsAtMostTheLineAndNotBesideAnOffer() throws Exception {
        Sales line = line(item(false), PIECE, 10);
        InvoiceLineEditService service = editService(DocumentType.SALES,
                new TrackingRepository(item(false)), (item, price, tier) -> false);
        service.editQuantity(line, 3.0);
        service.editDiscount(line, 30.0);
        assertEquals(0, line.getTotal_after_discount());
        assertThrows(com.hamza.controlsfx.error.UserValidationException.class, () -> service.editDiscount(line, 30.01));

        line.setOfferId(4);
        line.setOfferName("عرض");
        assertThrows(com.hamza.controlsfx.error.UserValidationException.class, () -> service.editDiscount(line, 1.0));
    }

    @Test
    void quantityAndDiscountEditsRecalculateTheLine() throws Exception {
        Sales line = line(item(false), PIECE, 10);
        InvoiceLineEditService service = editService(DocumentType.SALES_RETURN,
                new TrackingRepository(item(false)), (item, price, tier) -> false);

        service.editQuantity(line, 3.0);
        service.editDiscount(line, 4.0);

        assertEquals(30, line.getTotal());
        assertEquals(26, line.getTotal_after_discount());
    }

    @Test
    void catalogFailureLeavesTheInvoiceLinePriceUnchanged() {
        ItemsModel stored = item(false);
        TrackingRepository repository = new TrackingRepository(stored);
        repository.failSave = true;
        Sales line = line(item(false), CARTON, 100);
        InvoiceLineEditService service = editService(DocumentType.SALES_RETURN,
                repository, (item, price, tier) -> true);

        assertThrows(DaoException.class,
                () -> service.editPrice(line, 120.0, true, 1));
        assertEquals(100, line.getPrice());
    }

    @Test
    void purchaseUnitWithOwnBuyPriceDoesNotRewriteTheBasePrice() throws Exception {
        ItemsModel stored = item(true);
        TrackingRepository repository = new TrackingRepository(stored);
        boolean[] priceUpdated = {false};
        InvoiceItemCatalogService catalog = new InvoiceItemCatalogService(
                DocumentType.PURCHASE, repository, (item, price, tier) -> {
                    priceUpdated[0] = true;
                    return true;
                });

        catalog.updateBasePrice(stored.getId(), 1, CARTON, 96, 1);

        assertFalse(priceUpdated[0]);
        assertEquals(0, repository.saves);
        assertEquals(7.5, stored.getBuyPrice());
    }

    @Test
    void derivedUnitPriceUpdatesTheBasePriceUsingTheFactor() throws Exception {
        ItemsModel stored = item(false);
        TrackingRepository repository = new TrackingRepository(stored);
        InvoiceItemCatalogService catalog = new InvoiceItemCatalogService(
                DocumentType.PURCHASE, repository, (item, price, tier) -> {
                    item.setBuyPrice(price);
                    return true;
                });

        catalog.updateBasePrice(stored.getId(), 1, CARTON, 96, 1);

        assertEquals(8, stored.getBuyPrice());
        assertEquals(1, repository.saves);
    }

    @Test
    void nameEditPersistsOnlyTheNameThenUpdatesTheLine() throws Exception {
        ItemsModel stored = item(false);
        ItemsModel displayed = item(false);
        TrackingRepository repository = new TrackingRepository(stored);
        InvoiceLineEditService service = editService(DocumentType.SALES,
                repository, (item, price, tier) -> {
                    throw new AssertionError("name edit must not update a price");
                });
        Sales line = line(displayed, PIECE, 10);

        service.editName(line, "  اسم جديد  ");

        assertEquals("اسم جديد", stored.getNameItem());
        assertEquals("اسم جديد", displayed.getNameItem());
        assertEquals(1, repository.saves);
    }

    @Test
    void writingAnInvoiceIsNotPermissionToRewriteTheItem() {
        // The row these two write is the item, not the invoice, so they ask for
        // items.update - and nothing between the cell editor and the write asked for
        // anything before this. A cashier holding only sales.create could rename an item
        // and rewrite the price every future invoice would quote.
        session.signIn(7, "cashier", Set.of(AppPermissions.SALES_CREATE));
        ItemsModel stored = item(false);
        TrackingRepository repository = new TrackingRepository(stored);
        InvoiceLineEditService service = editService(DocumentType.SALES,
                repository, (item, price, tier) -> true);
        Sales line = line(item(false), PIECE, 10);

        assertThrows(BusinessRuleException.class, () -> service.editName(line, "اسم جديد"));
        assertThrows(BusinessRuleException.class, () -> service.editPrice(line, 20.0, true, 1));

        assertEquals(0, repository.saves);
        assertEquals("صنف", stored.getNameItem());
        assertEquals(10, line.getPrice(), "a refused catalogue write must not move the line either");
    }

    @Test
    void aPriceEditThatLeavesTheCatalogAloneNeedsNoItemPermission() throws Exception {
        // The common case: the operator prices one line differently without ticking
        // "update the item's price". Nothing writes an item, so nothing may ask for
        // items.update - guarding the wrong method here would break every till.
        session.signIn(7, "cashier", Set.of(AppPermissions.SALES_CREATE));
        TrackingRepository repository = new TrackingRepository(item(false));
        InvoiceLineEditService service = editService(DocumentType.SALES_RETURN,
                repository, (item, price, tier) -> {
                    throw new AssertionError("the catalogue must not be touched");
                });
        Sales line = line(item(false), PIECE, 10);

        service.editPrice(line, 20.0, false, 1);

        assertEquals(20, line.getPrice());
        assertEquals(0, repository.saves);
    }

    @Test
    void refusesEditingThePriceOrDiscountOfALinePickedFromAnInvoice() {
        // ReturnCostResolver refuses this at save anyway; saying so at the moment of
        // the edit is the difference between a correction and a wasted document.
        Sales line = line(item(false), PIECE, 10);
        line.setSourceLineId(501);
        InvoiceLineEditService service = editService(DocumentType.SALES_RETURN,
                new TrackingRepository(item(false)), (item, price, tier) -> false);

        assertThrows(BusinessRuleException.class,
                () -> service.editPrice(line, 999.0, false, 1));
        assertThrows(BusinessRuleException.class,
                () -> service.editDiscount(line, 50.0));
        assertEquals(10, line.getPrice());
    }

    @Test
    void aLineWithNoSourceKeepsItsEditablePriceAndDiscount() throws Exception {
        // A free return, and every ordinary invoice line - nothing to hold them to.
        Sales line = line(item(false), PIECE, 10);
        InvoiceLineEditService service = editService(DocumentType.SALES_RETURN,
                new TrackingRepository(item(false)), (item, price, tier) -> false);

        service.editPrice(line, 30.0, false, 1);
        service.editDiscount(line, 5.0);

        assertEquals(30, line.getPrice());
        assertEquals(5, line.getDiscount());
    }

    @Test
    void aPickedReturnLinesDiscountShareFollowsItsQuantity() throws Exception {
        // Sold 5 with 10 off the line; 2 of them picked, carrying 4. Editing the 2 to 3 used
        // to keep the 4, the save then refused it for not being 6, and editDiscount refuses
        // to let anybody type the 6 - the row could only be deleted and picked again.
        Sales line = line(item(false), PIECE, 10);
        line.setSourceLineId(501);
        line.setQuantity(2);
        line.setDiscount(4);
        InvoiceLineEditService service = new InvoiceLineEditService(
                DocumentType.SALES_RETURN,
                new InvoiceItemCatalogService(DocumentType.SALES_RETURN,
                        new TrackingRepository(item(false)), (item, price, tier) -> false),
                () -> 1,
                sourceLineId -> java.util.Optional.of(
                        new InvoiceLineEditService.SourceLineTerms.Terms(5, 10)));

        service.editQuantity(line, 3.0);

        assertEquals(6, line.getDiscount());
        assertEquals(24, line.getTotal_after_discount());
    }

    @Test
    void aShareIsWorkedOutFromTheSourceLineNotScaledFromTheRoundedOneOnTheRow() throws Exception {
        // 10 off 3 sold: one of them carries 3.33. Scaled back up to three that is 9.99,
        // a piastre short of the 10 the save expects.
        Sales line = line(item(false), PIECE, 10);
        line.setSourceLineId(501);
        line.setDiscount(3.33);
        InvoiceLineEditService service = new InvoiceLineEditService(
                DocumentType.SALES_RETURN,
                new InvoiceItemCatalogService(DocumentType.SALES_RETURN,
                        new TrackingRepository(item(false)), (item, price, tier) -> false),
                () -> 1,
                sourceLineId -> java.util.Optional.of(
                        new InvoiceLineEditService.SourceLineTerms.Terms(3, 10)));

        service.editQuantity(line, 3.0);

        assertEquals(10, line.getDiscount());
    }

    @Test
    void aUnitChangeTakesThatUnitsPriceAndKeepsTheQuantityAsTyped() throws Exception {
        // Three pieces become three cartons at the carton's price - not thirty-six pieces,
        // and not three cartons at the piece's price.
        Sales line = line(item(false), PIECE, 10);
        line.setQuantity(3);
        InvoiceLineService.recalculate(line);
        InvoiceLineEditService service = editService(DocumentType.SALES,
                new TrackingRepository(item(false)), (item, price, tier) -> false);

        service.editUnit(line, new InvoiceItemSelectionService.UnitSelection(CARTON, 120, 0));

        assertEquals(CARTON, line.getUnitsType());
        assertEquals(3, line.getQuantity());
        assertEquals(120, line.getPrice());
        assertEquals(360, line.getTotal_after_discount());
    }

    @Test
    void aUnitChangeOnASaleIsHeldToThatUnitsCost() {
        // A carton costs 12 x 7.5 = 90; a carton "priced" at 50 is the piece price times
        // something, and a sale below cost is refused whichever way the price arrived.
        Sales line = line(item(false), PIECE, 10);
        InvoiceLineEditService service = editService(DocumentType.SALES,
                new TrackingRepository(item(false)), (item, price, tier) -> false);

        assertThrows(BusinessRuleException.class, () -> service.editUnit(line,
                new InvoiceItemSelectionService.UnitSelection(CARTON, 50, 0)));
        assertEquals(PIECE, line.getUnitsType());
        assertEquals(10, line.getPrice());
    }

    @Test
    void aLinePickedFromAnInvoiceKeepsItsUnit() {
        Sales line = line(item(false), PIECE, 10);
        line.setSourceLineId(501);
        InvoiceLineEditService service = editService(DocumentType.SALES_RETURN,
                new TrackingRepository(item(false)), (item, price, tier) -> false);

        assertThrows(BusinessRuleException.class, () -> service.editUnit(line,
                new InvoiceItemSelectionService.UnitSelection(CARTON, 120, 0)));
        assertEquals(PIECE, line.getUnitsType());
    }

    @Test
    void theEntryRowTakesNoUnit() {
        // The quick screen's trailing row names no item; nothing may be set on it.
        Sales entryRow = new Sales();
        entryRow.setItems(new ItemsModel());
        InvoiceLineEditService service = editService(DocumentType.SALES,
                new TrackingRepository(item(false)), (item, price, tier) -> false);

        assertThrows(com.hamza.controlsfx.error.UserValidationException.class, () -> service.editUnit(
                entryRow, new InvoiceItemSelectionService.UnitSelection(CARTON, 120, 0)));
    }

    private static InvoiceLineEditService editService(
            DocumentType type, TrackingRepository repository,
            InvoiceItemCatalogService.ItemPriceUpdater updater) {
        return new InvoiceLineEditService(type,
                new InvoiceItemCatalogService(type, repository, updater), 1);
    }

    private static Sales line(ItemsModel item, UnitsModel unit, double price) {
        Sales line = new Sales();
        line.setItems(item);
        line.setUnitsType(unit);
        line.setQuantity(1);
        line.setPrice(price);
        line.setDiscount(0);
        InvoiceLineService.recalculate(line);
        return line;
    }

    private static ItemsModel item(boolean ownBuyPrice) {
        ItemsModel item = new ItemsModel(7, "B7", "صنف");
        item.setUnitsType(PIECE);
        item.setBuyPrice(7.5);
        item.setSelPrice1(10);

        ItemsUnitsModel base = new ItemsUnitsModel();
        base.setUnitsModel(PIECE);
        base.setQuantityForUnit(1);

        ItemsUnitsModel carton = new ItemsUnitsModel();
        carton.setUnitsModel(CARTON);
        carton.setQuantityForUnit(12);
        carton.setBuyPrice(ownBuyPrice ? 90 : 0);

        item.setItemsUnitsModelList(new ArrayList<>(List.of(base, carton)));
        return item;
    }

    private static final class TrackingRepository
            implements InvoiceItemCatalogService.ItemRepository {
        private final ItemsModel stored;
        private int saves;
        private boolean failSave;

        private TrackingRepository(ItemsModel stored) {
            this.stored = stored;
        }

        @Override
        public ItemsModel load(int itemId, int stockId) {
            return stored;
        }

        @Override
        public void save(ItemsModel item) throws DaoException {
            if (failSave) {
                throw new DaoException("تعذر حفظ الصنف");
            }
            saves++;
        }
    }
}
