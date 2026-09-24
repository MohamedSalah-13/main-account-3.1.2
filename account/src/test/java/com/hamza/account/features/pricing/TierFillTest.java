package com.hamza.account.features.pricing;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.ItemsChanged;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.observer.AppEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierFillTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    /** Juice: cost 8, retail 10, trade 0, wholesale 9; its carton priced by hand at 115 retail, 96 cost. */
    static TierFill.ItemSource juice() {
        return new TierFill.ItemSource(7, "عصير", "قطعة", d("8.00"), List.of(d("10.00"), d("0.00"), d("9.00")),
                List.of(new TierFill.UnitSource(2, "كرتونة", d("96.00"), List.of(d("115.00"), d("0.00"), d("0.00"))),
                        new TierFill.UnitSource(3, "شدة", d("0.00"), List.of(d("0.00"), d("0.00"), d("0.00")))));
    }

    static TierFill.ItemSource soap() {
        return new TierFill.ItemSource(9, "صابون", "قطعة", d("4.00"), List.of(d("0.00"), d("0.00"), d("0.00")),
                List.of());
    }

    @Test
    @DisplayName("trade is retail less 5%: the item's price, and the carton priced by hand from its own retail")
    void fromAnotherTier() {
        List<TierFill.Change> changes = TierFill.changes(List.of(juice()), 2,
                TierFillRule.fromTier(1, d("-5"), d("0.25")));
        assertEquals(List.of(
                new TierFill.Change(7, TierFill.ITEM, "عصير", "قطعة", d("0.00"), d("9.50")),
                new TierFill.Change(7, 2, "عصير", "كرتونة", d("0.00"), d("109.25"))), changes);
    }

    @Test
    @DisplayName("a unit that follows the item is left following it; an item with no source is left alone")
    void nothingToWorkFrom() {
        List<TierFill.Change> changes = TierFill.changes(List.of(juice(), soap()), 2,
                TierFillRule.fromTier(1, d("-5"), d("0.25")));
        assertTrue(changes.stream().noneMatch(change -> change.unitId() == 3), "the unit following the item");
        assertTrue(changes.stream().noneMatch(change -> change.itemId() == 9), "retail 0 fills nothing");
    }

    @Test
    @DisplayName("from the cost, and a figure already right is not a change")
    void fromTheCost() {
        List<TierFill.Change> changes = TierFill.changes(List.of(juice(), soap()), 3,
                TierFillRule.fromCost(d("12.5"), d("0.05")));
        // juice 8 x 1.125 = 9.00, which is already its wholesale price; the carton 96 x 1.125 = 108.00.
        assertEquals(List.of(
                new TierFill.Change(7, 2, "عصير", "كرتونة", d("0.00"), d("108.00")),
                new TierFill.Change(9, TierFill.ITEM, "صابون", "قطعة", d("0.00"), d("4.50"))), changes);
    }

    @Test
    @DisplayName("only the gaps: a price the tier already has is left as somebody typed it")
    void onlyMissing() {
        // Juice has a wholesale price of 9 the cost rule would move to 9.56; the carton and the soap have none.
        TierFillRule rule = TierFillRule.fromCost(d("19.5"), d("0.01"));
        List<TierFill.Change> everything = TierFill.changes(List.of(juice(), soap()), 3, rule, false);
        List<TierFill.Change> gaps = TierFill.changes(List.of(juice(), soap()), 3, rule, true);
        assertTrue(everything.stream().anyMatch(change -> change.itemId() == 7 && change.unitId() == TierFill.ITEM));
        assertTrue(gaps.stream().noneMatch(change -> change.itemId() == 7 && change.unitId() == TierFill.ITEM));
        assertEquals(everything.size() - 1, gaps.size(), "the carton and the soap are still filled");
    }

    @Nested
    @DisplayName("the service: a preview, then exactly the preview or nothing")
    class Service {

        private final List<String> journal = new ArrayList<>();
        private final List<AppEvent> announced = new ArrayList<>();
        private List<TierFill.ItemSource> stored = new ArrayList<>(List.of(juice()));
        private TierFillRule rule = TierFillRule.fromTier(1, d("-5"), d("0.25"));
        private UserSessionContext session;
        private TierFillService service;

        @BeforeEach
        void setUp() {
            session = new UserSessionContext();
            ServiceRegistry.register(UserSessionContext.class, session);
            PriceTierRepository tiers = new PriceTierRepository() {
                @Override
                public List<PriceTier> all() {
                    return List.of(new PriceTier(1, "قطاعي", true, null), new PriceTier(2, "تجزئة", true, rule),
                            new PriceTier(3, "جملة", true, null));
                }

                @Override
                public void lockAll() {
                }

                @Override
                public int update(PriceTier tier) {
                    return 1;
                }

                @Override
                public void clearNames(List<Integer> tierIds) {
                }

                @Override
                public Map<Integer, Integer> activeCustomersByTier() {
                    return Map.of();
                }
            };
            TierFillRepository catalogue = new TierFillRepository() {
                @Override
                public List<TierFill.ItemSource> readAll() {
                    return stored;
                }

                @Override
                public void lockCatalogue() {
                    journal.add("lock");
                }

                @Override
                public int writeItem(int itemId, int tierId, BigDecimal before, BigDecimal after) {
                    journal.add("item " + itemId + " " + after);
                    return 1;
                }

                @Override
                public int writeUnit(int itemId, int unitId, int tierId, BigDecimal before, BigDecimal after) {
                    journal.add("unit " + itemId + "/" + unitId + " " + after);
                    return 1;
                }
            };
            service = new TierFillService(tiers, catalogue, new PriceTierService.Transactions() {
                @Override
                public <T> T execute(TransactionTemplate.TransactionalSupplier<T> work) throws DaoException {
                    try {
                        return work.get();
                    } catch (DaoException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new DaoException(e);
                    }
                }
            }, announced::add);
        }

        @AfterEach
        void tearDown() {
            ServiceRegistry.register(UserSessionContext.class, null);
        }

        @Test
        void writesThePreviewAndTellsEveryTill() throws Exception {
            session.signIn(7, "manager", Set.of(AppPermissions.ITEMS_SHOW, AppPermissions.ITEMS_UPDATE,
                    AppPermissions.ITEMS_UNIT_PRICE_UPDATE));
            List<TierFill.Change> preview = service.preview(2, false);
            assertEquals(2, service.apply(2, false, preview));
            assertEquals(List.of("lock", "item 7 9.50", "unit 7/2 109.25"), journal);
            assertEquals(List.of(new ItemsChanged()), announced);
        }

        @Test
        @DisplayName("a price moved since the preview refuses the whole of it")
        void refusesAStalePreview() throws Exception {
            session.signIn(7, "manager", Set.of(AppPermissions.ITEMS_SHOW, AppPermissions.ITEMS_UPDATE,
                    AppPermissions.ITEMS_UNIT_PRICE_UPDATE));
            List<TierFill.Change> preview = service.preview(2, false);
            stored = List.of(new TierFill.ItemSource(7, "عصير", "قطعة", d("8.00"),
                    List.of(d("11.00"), d("0.00"), d("9.00")), juice().units()));
            assertThrows(BusinessRuleException.class, () -> service.apply(2, false, preview));
            assertEquals(List.of("lock"), journal, "nothing written");
        }

        @Test
        @DisplayName("a unit's own price needs its own permission; a cost rule needs the cost")
        void permissions() throws Exception {
            session.signIn(7, "clerk", Set.of(AppPermissions.ITEMS_SHOW, AppPermissions.ITEMS_UPDATE));
            List<TierFill.Change> preview = service.preview(2, false);
            assertThrows(BusinessRuleException.class, () -> service.apply(2, false, preview));

            rule = TierFillRule.fromCost(d("10"), d("0.05"));
            assertThrows(BusinessRuleException.class, () -> service.preview(2, false),
                    "a price that is the cost plus a known percentage is the cost");
        }

        @Test
        void aTierWithNoRuleHasNothingToApply() {
            session.signIn(7, "manager", Set.of(AppPermissions.ITEMS_SHOW));
            UserValidationException refused = assertThrows(UserValidationException.class, () -> service.preview(3, false));
            assertEquals("pricing.fill.error.no.rule", refused.getMessage());
        }
    }
}
