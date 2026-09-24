package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.offers.Offer;
import com.hamza.account.features.offers.OfferKind;
import com.hamza.account.features.offers.OfferStatus;
import com.hamza.account.features.offers.OfferTarget;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.MainGroups;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.SubGroups;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.database.DaoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The offers between an invoice's lines and the engine: on the screen, and at the save. */
class InvoiceOffersTest {

    static final LocalDate DAY = LocalDate.of(2026, 9, 24);

    static final Offer TEN_PERCENT_DETERGENTS = new Offer(1, "المنظفات 10%", OfferKind.PERCENT, OfferStatus.ACTIVE,
            DAY.minusDays(5), null, null, 0, new BigDecimal("10"), null, null, null, null,
            List.of(OfferTarget.subGroup(5)), Set.of(), null);

    static Sales line(int itemId, int subGroup, double quantity, double price, double discount) {
        ItemsModel item = new ItemsModel(itemId, "B" + itemId, "صنف " + itemId);
        SubGroups sub = new SubGroups(subGroup);
        MainGroups main = new MainGroups();
        main.setId(9);
        sub.setMainGroups(main);
        item.setSubGroups(sub);
        UnitsModel unit = new UnitsModel(1, "قطعة", 1);
        Sales line = new Sales();
        line.setItems(item);
        line.setUnitsType(unit);
        line.setQuantity(quantity);
        line.setPrice(price);
        line.setDiscount(discount);
        InvoiceLineService.recalculate(line);
        return line;
    }

    @Test
    @DisplayName("example 1 on the screen: soap 3 x 40 takes 12.00, which replaces the manual discount")
    void appliedOnTheLines() throws DaoException {
        Sales soap = line(11, 5, 3, 40, 2);
        Sales rice = line(20, 7, 2, 30, 1);
        InvoiceOfferPreview preview = new InvoiceOfferPreview(ids -> Map.of());
        preview.setOffers(List.of(TEN_PERCENT_DETERGENTS));

        assertTrue(preview.run(List.of(soap, rice), DAY, 1));
        assertEquals(1, soap.getOfferId());
        assertEquals("المنظفات 10%", soap.getOfferName());
        assertEquals(12.0, soap.getDiscount());
        assertEquals(108.0, soap.getTotal_after_discount());
        assertNull(rice.getOfferId());
        assertEquals(1.0, rice.getDiscount(), "a line no offer reaches keeps its manual discount");
        assertEquals(new BigDecimal("12.00"), preview.last().discount());
        assertFalse(preview.run(List.of(soap, rice), DAY, 1), "run again, nothing moves");
    }

    @Test
    @DisplayName("an offer that leaves a line takes its discount with it; the manual one it replaced is not back")
    void anOfferLeaves() throws DaoException {
        Sales soap = line(11, 5, 3, 40, 0);
        InvoiceOfferPreview preview = new InvoiceOfferPreview(ids -> Map.of());
        preview.setOffers(List.of(TEN_PERCENT_DETERGENTS));
        preview.run(List.of(soap), DAY, 1);
        assertTrue(preview.run(List.of(soap), DAY.minusDays(10), 1), "a date before the offer");
        assertNull(soap.getOfferId());
        assertEquals(0.0, soap.getDiscount());
        assertTrue(preview.clear(List.of()) == false);
    }

    @Test
    @DisplayName("an item loaded without its groups has them asked of the database once")
    void groupsAskedOnce() throws DaoException {
        Sales soap = line(11, 5, 1, 40, 0);
        soap.getItems().setSubGroups(null);
        List<Collection<Integer>> asked = new ArrayList<>();
        InvoiceOfferPreview preview = new InvoiceOfferPreview(ids -> {
            asked.add(List.copyOf(ids));
            return Map.of(11, new InvoiceOffers.ItemGroups(5, 9));
        });
        preview.setOffers(List.of(TEN_PERCENT_DETERGENTS));
        preview.run(List.of(soap), DAY, 1);
        preview.run(List.of(soap), DAY, 1);
        assertEquals(1, asked.size());
        assertEquals(4.0, soap.getDiscount());
    }

    @Test
    @DisplayName("the offers' part of the discounts, which a delegate's ceiling leaves aside (example 7)")
    void offerDiscount() throws DaoException {
        Sales soap = line(11, 5, 3, 40, 0);
        Sales rice = line(20, 7, 2, 90, 20);
        InvoiceOfferPreview preview = new InvoiceOfferPreview(ids -> Map.of());
        preview.setOffers(List.of(new Offer(2, "40 off", OfferKind.AMOUNT, OfferStatus.ACTIVE, DAY, null, null, 0,
                null, new BigDecimal("13.3333"), null, null, null, List.of(OfferTarget.item(11)), Set.of(), null)));
        preview.run(List.of(soap, rice), DAY, 1);
        // 300 before discounts: 40 from the offer, 20 by hand - the ceiling judges the 20
        assertEquals(new BigDecimal("40.00"), InvoiceOffers.offerDiscount(List.of(soap, rice)));
    }

    @Nested
    @DisplayName("the save")
    class Save {

        private final List<Offer> inForce = new ArrayList<>(List.of(TEN_PERCENT_DETERGENTS));

        private InvoiceOffers offers(boolean enabled) {
            return new InvoiceOffers(new InvoiceOffers.Source() {
                @Override public boolean enabled() { return enabled; }
                @Override public List<Offer> forDocument(LocalDate day, Set<Integer> recorded) { return inForce; }
                @Override public Set<Integer> offersOnDocument(int invoiceNumber) { return Set.of(); }
                @Override public String nameOf(int offerId) { return "عرض " + offerId; }
                @Override public Map<Integer, InvoiceOffers.ItemGroups> groupsOf(Collection<Integer> itemIds) {
                    return Map.of(11, new InvoiceOffers.ItemGroups(5, 9));
                }
            });
        }

        @Test
        @DisplayName("the lines the screen worked out pass; an offer ended since is refused, naming it")
        void judged() throws DaoException {
            Sales soap = line(11, 5, 3, 40, 0);
            InvoiceOfferPreview preview = new InvoiceOfferPreview(ids -> Map.of());
            preview.setOffers(inForce);
            preview.run(List.of(soap), DAY, 1);
            assertDoesNotThrow(() -> offers(true).judge(DocumentType.SALES, false, DAY, 1, 0, List.of(soap)));

            inForce.clear();
            InvoiceValidationException refused = assertThrows(InvoiceValidationException.class,
                    () -> offers(true).judge(DocumentType.SALES, false, DAY, 1, 0, List.of(soap)));
            assertEquals(InvoiceSaveValidator.Target.LINES, refused.target());
            assertTrue(refused.getMessage().contains("عرض 1"), refused.getMessage());
        }

        @Test
        @DisplayName("nothing judged without the add-on, on a purchase, or by the save before offers existed")
        void notJudged() {
            Sales claimed = line(11, 5, 3, 40, 5);
            claimed.setOfferId(99);
            claimed.setOfferDiscount(new BigDecimal("5"));
            assertDoesNotThrow(() -> offers(false).judge(DocumentType.SALES, false, DAY, 1, 0, List.of(claimed)));
            assertDoesNotThrow(() -> offers(true).judge(DocumentType.PURCHASE, false, DAY, 1, 0, List.of(claimed)));
            assertDoesNotThrow(() -> InvoiceOffers.none().judge(DocumentType.SALES, false, DAY, 1, 0, List.of(claimed)));
        }

        @Test
        @DisplayName("a document in a foreign currency claims no offer (ق-ع١١)")
        void foreign() {
            Sales claimed = line(11, 5, 3, 40, 12);
            claimed.setOfferId(1);
            claimed.setOfferDiscount(new BigDecimal("12"));
            assertThrows(InvoiceValidationException.class,
                    () -> offers(true).judge(DocumentType.SALES, true, DAY, 1, 0, List.of(claimed)));
            Sales plain = line(11, 5, 3, 40, 12);
            assertDoesNotThrow(() -> offers(true).judge(DocumentType.SALES, true, DAY, 1, 0, List.of(plain)));
        }
    }
}
