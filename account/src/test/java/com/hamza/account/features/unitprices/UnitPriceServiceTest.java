package com.hamza.account.features.unitprices;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.ItemsChanged;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.observer.AppEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The save's rules against a repository in memory: who may change what, that a figure moved by
 * somebody else refuses the whole save, and that only the figures the operator changed are written.
 * <p>
 * The session signs in as user 9, never 1 - user 1 bypasses every permission, and a test signed in
 * as it would pass without asking any.
 */
class UnitPriceServiceTest {

    private final FakeRepository repository = new FakeRepository();
    private final List<AppEvent> announced = new ArrayList<>();
    private final UnitPriceService service = new UnitPriceService(repository,
            UnitPriceTransactionExecutor.direct(), announced::add);

    private static void signIn(PermissionKey... permissions) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(9, "operator", List.of(permissions));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private static UnitPriceSaveCommand command(PriceChange... changes) {
        return new UnitPriceSaveCommand(List.of(changes), 9);
    }

    private static PriceChange cartonSell(double before, double after) {
        return new PriceChange(1025, 2, new Prices(0, before, 114, 0), new Prices(0, after, 114, 0));
    }

    @Test
    @DisplayName("a unit's price is saved with the unit-price permission alone, and announced to the other tills")
    void savesAUnitPrice() throws Exception {
        signIn(AppPermissions.ITEMS_UNIT_PRICE_UPDATE, AppPermissions.SHOW_COLUMN_BUY_PRICE);

        UnitPriceSaveResult result = service.save(command(cartonSell(115, 0)));

        assertEquals(new UnitPriceSaveResult(0, 1), result);
        assertEquals(0.0, repository.stored.get(1025).unit(2).own().sell1(), "cleared: now automatic");
        assertEquals(1, announced.size());
        assertInstanceOf(ItemsChanged.class, announced.getFirst());
    }

    @Test
    @DisplayName("a unit's price needs items.unit.price.update, whatever else the user holds")
    void unitNeedsItsPermission() {
        signIn(AppPermissions.ITEMS_UPDATE, AppPermissions.SHOW_COLUMN_BUY_PRICE);

        assertThrows(BusinessRuleException.class, () -> service.save(command(cartonSell(115, 0))));
        assertEquals(115.0, repository.stored.get(1025).unit(2).own().sell1());
    }

    @Test
    @DisplayName("the item's own price needs items.update - the unit-price permission is narrower")
    void itemNeedsItemsUpdate() {
        signIn(AppPermissions.ITEMS_UNIT_PRICE_UPDATE, AppPermissions.SHOW_COLUMN_BUY_PRICE);
        PriceChange item = new PriceChange(1025, PriceChange.ITEM, new Prices(8, 10, 9.5, 0), new Prices(8, 11, 9.5, 0));

        assertThrows(BusinessRuleException.class, () -> service.save(command(item)));
    }

    @Test
    @DisplayName("changing a cost needs the permission to see costs")
    void costNeedsItsColumn() {
        signIn(AppPermissions.ITEMS_UNIT_PRICE_UPDATE);
        PriceChange cost = new PriceChange(1025, 2, new Prices(0, 115, 114, 0), new Prices(90, 115, 114, 0));

        assertThrows(BusinessRuleException.class, () -> service.save(command(cost)));
    }

    @Test
    @DisplayName("a figure somebody else changed since the screen read it refuses the whole save")
    void staleFigureRefusesEverything() {
        signIn(AppPermissions.ITEMS_UNIT_PRICE_UPDATE, AppPermissions.ITEMS_UPDATE, AppPermissions.SHOW_COLUMN_BUY_PRICE);
        PriceChange box = new PriceChange(1025, 3, Prices.ZERO, new Prices(0, 62, 0, 0));
        PriceChange stale = cartonSell(118, 120);

        BusinessRuleException refusal = assertThrows(BusinessRuleException.class, () -> service.save(command(box, stale)));

        assertEquals("unit.prices.error.concurrent", refusal.getMessage());
        assertEquals(0, repository.writes, "nothing is written when one row is stale");
        assertTrue(announced.isEmpty());
    }

    @Test
    @DisplayName("a field this save did not change is neither compared nor written - another till's edit to it stands")
    void onlyChangedFieldsAreWritten() throws Exception {
        signIn(AppPermissions.ITEMS_UNIT_PRICE_UPDATE, AppPermissions.SHOW_COLUMN_BUY_PRICE);
        // Another till moved the second tier to 113 after this screen read 114.
        repository.stored.put(1025, repository.stored.get(1025).withUnits(List.of(
                new UnitPriceLine(2, "كرتونة", 12, new Prices(0, 115, 113, 0)),
                new UnitPriceLine(3, "علبة", 6, Prices.ZERO))));

        service.save(command(cartonSell(115, 116)));

        Prices stored = repository.stored.get(1025).unit(2).own();
        assertEquals(116.0, stored.sell1());
        assertEquals(113.0, stored.sell2());
    }

    @Test
    @DisplayName("a reader who may not see costs is handed none, and a save from that reader keeps the stored cost")
    void maskedCostIsNeverWrittenBack() throws Exception {
        signIn(AppPermissions.ITEMS_SHOW, AppPermissions.ITEMS_UNIT_PRICE_UPDATE);
        repository.stored.put(1025, repository.stored.get(1025).withUnits(List.of(
                new UnitPriceLine(2, "كرتونة", 12, new Prices(90, 115, 114, 0)),
                new UnitPriceLine(3, "علبة", 6, Prices.ZERO))));

        UnitPricePage page = service.page(UnitPriceFilter.EMPTY.withBelowCostOnly(true), 50, 0);
        UnitPriceItem read = page.items().getFirst();
        assertFalse(page.costVisible());
        assertEquals(0.0, read.prices().buy());
        assertEquals(0.0, read.unit(2).own().buy());
        assertFalse(repository.lastFilter.belowCostOnly(), "below cost is a question about costs");

        PriceChange change = new PriceChange(1025, 2, read.unit(2).own(), read.unit(2).own().with(PriceField.SELL_1, 116));
        service.save(command(change));

        assertEquals(90.0, repository.stored.get(1025).unit(2).own().buy());
        assertEquals(8.0, repository.stored.get(1025).prices().buy());
    }

    @Test
    @DisplayName("a unit priced below its cost is refused with its name, and nothing is written")
    void belowCostIsRefused() {
        signIn(AppPermissions.ITEMS_UNIT_PRICE_UPDATE, AppPermissions.SHOW_COLUMN_BUY_PRICE);

        BusinessRuleException refusal = assertThrows(BusinessRuleException.class,
                () -> service.save(command(cartonSell(115, 90))));

        assertTrue(refusal.getMessage().contains("كرتونة") || refusal.getMessage().contains("unit.prices"),
                refusal.getMessage());
        assertEquals(0, repository.writes);
    }

    @Test
    @DisplayName("a unit that no longer exists is a conflict, not a silent no-op")
    void missingUnitIsAConflict() {
        signIn(AppPermissions.ITEMS_UNIT_PRICE_UPDATE);
        PriceChange ghost = new PriceChange(1025, 77, Prices.ZERO, new Prices(0, 5, 0, 0));

        assertThrows(BusinessRuleException.class, () -> service.save(command(ghost)));
    }

    @Test
    @DisplayName("an empty save asks nothing and writes nothing")
    void emptySave() throws Exception {
        signIn();

        assertEquals(0, service.save(command()).total());
        assertTrue(announced.isEmpty());
    }

    private static final class FakeRepository implements UnitPriceRepository {
        final Map<Integer, UnitPriceItem> stored = new LinkedHashMap<>();
        int writes;
        UnitPriceFilter lastFilter;

        FakeRepository() {
            stored.put(1025, UnitPriceDraftTest.juice());
        }

        @Override
        public List<UnitPriceItem> findPage(UnitPriceFilter filter, int limit, int offset) {
            lastFilter = filter;
            return List.copyOf(stored.values());
        }

        @Override
        public int count(UnitPriceFilter filter) {
            return stored.size();
        }

        @Override
        public List<UnitPriceItem> findAll(UnitPriceFilter filter) {
            lastFilter = filter;
            return List.copyOf(stored.values());
        }

        @Override
        public Map<Integer, UnitPriceItem> lockItems(Set<Integer> itemIds) {
            Map<Integer, UnitPriceItem> result = new LinkedHashMap<>();
            for (Integer id : itemIds) {
                if (stored.containsKey(id)) result.put(id, stored.get(id));
            }
            return result;
        }

        @Override
        public int updateItemPrices(int itemId, Prices prices, int userId) throws DaoException {
            writes++;
            stored.put(itemId, stored.get(itemId).withPrices(prices));
            return 1;
        }

        @Override
        public int updateUnitPrices(int itemId, int unitId, Prices prices, int userId) {
            writes++;
            UnitPriceItem item = stored.get(itemId);
            stored.put(itemId, item.withUnits(item.units().stream()
                    .map(unit -> unit.unitId() == unitId ? unit.withOwn(prices) : unit)
                    .toList()));
            return 1;
        }
    }
}
