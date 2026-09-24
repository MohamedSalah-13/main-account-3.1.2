package com.hamza.account.features.pricing;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.PriceTiersChanged;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.observer.AppEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceTierServiceTest {

    private final List<String> journal = new ArrayList<>();
    private final List<AppEvent> announced = new ArrayList<>();
    private final Map<Integer, PriceTier> stored = new HashMap<>();
    private UserSessionContext session;
    private PriceTierService service;

    @BeforeEach
    void setUp() {
        stored.put(1, new PriceTier(1, "قطاعي", true, null));
        stored.put(2, new PriceTier(2, "تجزئة", true, null));
        stored.put(3, new PriceTier(3, "جملة", true, null));
        session = new UserSessionContext();
        ServiceRegistry.register(UserSessionContext.class, session);
        service = new PriceTierService(new PriceTierRepository() {
            @Override
            public List<PriceTier> all() {
                return new ArrayList<>(stored.values());
            }

            @Override
            public void lockAll() {
                journal.add("lock");
            }

            @Override
            public int update(PriceTier tier) {
                journal.add("update " + tier.id());
                stored.put(tier.id(), tier);
                return 1;
            }

            @Override
            public void clearNames(List<Integer> tierIds) {
                journal.add("clear " + tierIds);
            }

            @Override
            public Map<Integer, Integer> activeCustomersByTier() {
                return Map.of(3, 4);
            }
        }, new PriceTierService.Transactions() {
            @Override
            public <T> T execute(com.hamza.controlsfx.database.TransactionTemplate.TransactionalSupplier<T> work)
                    throws com.hamza.controlsfx.database.DaoException {
                try {
                    return work.get();
                } catch (com.hamza.controlsfx.database.DaoException e) {
                    throw e;
                } catch (Exception e) {
                    throw new com.hamza.controlsfx.database.DaoException(e);
                }
            }
        }, announced::add);
    }

    @AfterEach
    void tearDown() {
        ServiceRegistry.register(UserSessionContext.class, null);
    }

    private List<PriceTier> edited(PriceTier replacement) {
        List<PriceTier> tiers = new ArrayList<>(new PriceTierCatalog(new ArrayList<>(stored.values())).all());
        tiers.set(replacement.id() - 1, replacement);
        return tiers;
    }

    @Test
    @DisplayName("renaming a tier asks sel.price.update, which nothing used to read")
    void savingNeedsThePermission() {
        session.signIn(7, "cashier", Set.of(AppPermissions.SALES_CREATE));
        assertThrows(BusinessRuleException.class,
                () -> service.save(edited(stored.get(3).withName("جملة الجملة"))));
        assertTrue(journal.isEmpty(), "refused before anything was locked");
    }

    @Test
    @DisplayName("only what changed is written, a changed name is moved aside first, and every till is told")
    void writesTheChangeAndAnnounces() throws Exception {
        session.signIn(7, "manager", Set.of(AppPermissions.SEL_PRICE_UPDATE));
        int written = service.save(edited(stored.get(3).withName("جملة كبار")));
        assertEquals(1, written);
        assertEquals(List.of("lock", "clear [3]", "update 3"), journal);
        assertEquals(List.of(new PriceTiersChanged()), announced);
        assertEquals("جملة كبار", service.catalog().name(3));
    }

    @Test
    @DisplayName("nothing changed, nothing written and nobody told")
    void nothingChanged() throws Exception {
        session.signIn(7, "manager", Set.of(AppPermissions.SEL_PRICE_UPDATE));
        assertEquals(0, service.save(new PriceTierCatalog(new ArrayList<>(stored.values())).all()));
        assertEquals(List.of("lock"), journal);
        assertTrue(announced.isEmpty());
    }

    @Test
    @DisplayName("a rule and a switch-off move no name")
    void ruleWithoutRename() throws Exception {
        session.signIn(7, "manager", Set.of(AppPermissions.SEL_PRICE_UPDATE));
        service.save(edited(stored.get(2).withActive(false)
                .withRule(TierFillRule.fromTier(1, new BigDecimal("-5"), new BigDecimal("0.25")))));
        assertEquals(List.of("lock", "clear []", "update 2"), journal);
    }

    @Test
    @DisplayName("each rule refuses with its own message key")
    void rules() {
        assertRefused("pricing.tier.error.first.active", edited(stored.get(1).withActive(false)));
        assertRefused("pricing.tier.error.name.required", edited(stored.get(2).withName(" ")));
        assertRefused("pricing.tier.error.name.long", edited(stored.get(2).withName("x".repeat(51))));
        assertRefused("pricing.tier.error.name.duplicate", edited(stored.get(2).withName("جملة")));
        assertRefused("pricing.tier.error.rule.self", edited(stored.get(2)
                .withRule(TierFillRule.fromTier(2, BigDecimal.ONE, BigDecimal.ONE))));
        assertRefused("pricing.tier.error.rule.percent", edited(stored.get(2)
                .withRule(TierFillRule.fromCost(new BigDecimal("1000.5"), BigDecimal.ONE))));
        assertRefused("pricing.tier.error.rule.rounding", edited(stored.get(2)
                .withRule(TierFillRule.fromCost(BigDecimal.ONE, new BigDecimal("0.001")))));
    }

    @Test
    @DisplayName("two names swapped in one save are legal - the index is side-stepped, not met")
    void swappedNames() throws Exception {
        session.signIn(7, "manager", Set.of(AppPermissions.SEL_PRICE_UPDATE));
        List<PriceTier> tiers = new ArrayList<>(new PriceTierCatalog(new ArrayList<>(stored.values())).all());
        tiers.set(1, tiers.get(1).withName("جملة"));
        tiers.set(2, tiers.get(2).withName("تجزئة"));
        assertEquals(2, service.save(tiers));
        assertEquals(List.of("lock", "clear [2, 3]", "update 2", "update 3"), journal);
    }

    private static void assertRefused(String key, List<PriceTier> tiers) {
        UserValidationException refused = assertThrows(UserValidationException.class,
                () -> PriceTierService.requireValid(tiers));
        assertEquals(key, refused.getMessage());
    }
}
