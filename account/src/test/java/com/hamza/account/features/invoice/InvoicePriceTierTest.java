package com.hamza.account.features.invoice;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.interfaces.impl_invoiceBuy.SalesInvoice;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvoicePriceTierTest {

    /** A customer on tier 2; tier 3 switched off; invoice 50 saved at tier 3 before it was. */
    private final Map<Integer, Double> storedLinePrices = new HashMap<>();
    private final List<String> written = new ArrayList<>();
    private UserSessionContext session;
    private InvoicePriceTier tiers;

    @BeforeEach
    void setUp() {
        session = new UserSessionContext();
        ServiceRegistry.register(UserSessionContext.class, session);
        tiers = new InvoicePriceTier(new InvoicePriceTier.Repository() {
            @Override
            public Integer storedTier(DocumentType type, int number) {
                return number == 50 ? 3 : null;
            }

            @Override
            public int partyTier(int partyId) {
                return partyId == 9 ? 2 : partyId == 8 ? 3 : 1;
            }

            @Override
            public boolean isActive(int tierId) {
                return tierId != 3;
            }

            @Override
            public String tierName(int tierId) {
                return "جملة";
            }

            @Override
            public Map<Integer, Double> storedLinePrices(int number) {
                return storedLinePrices;
            }

            @Override
            public void writeTier(DocumentType type, int number, Integer tierId) {
                written.add(type + " " + number + " " + tierId);
            }
        });
    }

    @AfterEach
    void tearDown() {
        ServiceRegistry.register(UserSessionContext.class, null);
    }

    private void signIn(PermissionKey... keys) {
        session.signIn(7, "cashier", Set.of(keys));
    }

    @Nested
    @DisplayName("the tier a document is stored at")
    class Decide {

        @Test
        @DisplayName("the customer's own tier needs no permission")
        void customersTier() throws Exception {
            signIn(AppPermissions.SALES_CREATE);
            assertEquals(2, tiers.decide(DocumentType.SALES, 9, 0, 2));
        }

        @Test
        @DisplayName("any other tier needs sales.price.tier.change")
        void anotherTier() throws Exception {
            signIn(AppPermissions.SALES_CREATE);
            assertThrows(BusinessRuleException.class, () -> tiers.decide(DocumentType.SALES, 9, 0, 1));
            signIn(AppPermissions.SALES_CREATE, AppPermissions.SALES_PRICE_TIER_CHANGE);
            assertEquals(1, tiers.decide(DocumentType.SALES, 9, 0, 1));
        }

        @Test
        @DisplayName("an edit keeps the tier it was saved at - even switched off, even once the customer moved")
        void storedTierIsKept() throws Exception {
            signIn(AppPermissions.SALES_UPDATE);
            assertEquals(3, tiers.decide(DocumentType.SALES, 9, 50, 3));
            assertEquals(3, tiers.decide(DocumentType.SALES, 9, 50, null), "a caller that knows no tier keeps it");
        }

        @Test
        @DisplayName("a tier switched off cannot be chosen anew, and one that does not exist never")
        void inactiveAndUnknown() {
            signIn(AppPermissions.SALES_CREATE, AppPermissions.SALES_PRICE_TIER_CHANGE);
            assertThrows(BusinessRuleException.class, () -> tiers.decide(DocumentType.SALES, 9, 0, 3));
            assertThrows(BusinessRuleException.class, () -> tiers.decide(DocumentType.SALES, 9, 0, 4));
        }

        @Test
        @DisplayName("a customer on a tier switched off is at tier 1, which then needs no permission")
        void customerOnASwitchedOffTier() throws Exception {
            signIn(AppPermissions.SALES_CREATE);
            assertEquals(1, tiers.decide(DocumentType.SALES, 8, 0, 1));
        }

        @Test
        @DisplayName("a return is priced from a tier too; a purchase never")
        void families() throws Exception {
            signIn(AppPermissions.SALES_RE_CREATE);
            assertEquals(2, tiers.decide(DocumentType.SALES_RETURN, 9, 0, 2));
            assertNull(tiers.decide(DocumentType.PURCHASE, 9, 0, 2));
            assertNull(tiers.decide(DocumentType.SALES, 9, 0, null), "a new document nobody tiered has none");
        }

        @Test
        void writesOnlyForTheCustomerFamilies() throws Exception {
            tiers.write(DocumentType.SALES, 12, 2);
            tiers.write(DocumentType.PURCHASE, 12, 2);
            assertEquals(List.of("SALES 12 2"), written);
        }
    }

    @Nested
    @DisplayName("a price under its line's list")
    class BelowList {

        private Sales line(int id, double price, String listPrice) {
            ItemsModel item = new ItemsModel(5, "B5", "صابون");
            Sales line = new SalesInvoice().object_TableData(id, 60, 5, price, 1, 0, price,
                    new UnitsModel(1, "قطعة", 1), item, null);
            line.setListPrice(listPrice == null ? null : new BigDecimal(listPrice));
            return line;
        }

        @Test
        @DisplayName("needs sales.price.below.list")
        void refused() {
            signIn(AppPermissions.SALES_CREATE);
            assertThrows(BusinessRuleException.class, () -> tiers.requireListPrices(DocumentType.SALES, 0,
                    List.of(line(0, 9.00, "10.00"))));
            signIn(AppPermissions.SALES_CREATE, AppPermissions.SALES_PRICE_BELOW_LIST);
            assertDoesNotThrow(() -> tiers.requireListPrices(DocumentType.SALES, 0,
                    List.of(line(0, 9.00, "10.00"))));
        }

        @Test
        @DisplayName("at or above the list, and a line with no list behind it, need nothing")
        void notBelow() {
            signIn(AppPermissions.SALES_CREATE);
            assertDoesNotThrow(() -> tiers.requireListPrices(DocumentType.SALES, 0, List.of(
                    line(0, 10.00, "10.00"), line(0, 10.004, "10.00"), line(0, 12, "10.00"), line(0, 1, null))));
        }

        @Test
        @DisplayName("a saved line whose price has not moved is not the corrector's decision")
        void unchangedStoredLine() {
            signIn(AppPermissions.SALES_UPDATE);
            storedLinePrices.put(41, 9.00);
            assertDoesNotThrow(() -> tiers.requireListPrices(DocumentType.SALES, 60,
                    List.of(line(41, 9.00, "10.00"))));
            assertThrows(BusinessRuleException.class, () -> tiers.requireListPrices(DocumentType.SALES, 60,
                    List.of(line(41, 8.50, "10.00"))));
        }

        @Test
        @DisplayName("a return is held to its source line instead, and a purchase has no list")
        void onlySales() {
            signIn(AppPermissions.SALES_RE_CREATE);
            assertDoesNotThrow(() -> tiers.requireListPrices(DocumentType.SALES_RETURN, 0,
                    List.of(line(0, 9.00, "10.00"))));
        }
    }

    @Test
    @DisplayName("the statements, pinned")
    void statements() {
        assertEquals("SELECT price_tier_id FROM total_sales WHERE invoice_number = ?",
                InvoicePriceTier.Jdbc.storedTierSql(DocumentType.SALES));
        assertEquals("SELECT price_tier_id FROM total_sales_re WHERE id = ?",
                InvoicePriceTier.Jdbc.storedTierSql(DocumentType.SALES_RETURN));
        assertEquals("UPDATE total_sales SET price_tier_id = ? WHERE invoice_number = ?",
                InvoicePriceTier.Jdbc.writeTierSql(DocumentType.SALES));
        assertEquals("SELECT price_id FROM custom WHERE id = ?", InvoicePriceTier.Jdbc.PARTY_TIER_SQL);
        assertEquals("SELECT id, price FROM sales WHERE invoice_number = ?", InvoicePriceTier.Jdbc.LINE_PRICES_SQL);
    }
}
