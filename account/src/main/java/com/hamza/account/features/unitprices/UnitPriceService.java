package com.hamza.account.features.unitprices;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.ItemsChanged;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads items with their units, and saves the prices the unit prices screen changed.
 * <p>
 * A save is one transaction and all or nothing, the way the group move is: every item it touches is
 * locked with its units, each changed figure is compared with what the screen read, and one figure
 * somebody else changed in the meantime refuses the whole save rather than overwriting their price.
 * Only the figures the operator changed are compared and only they are written, so a price edited
 * on another till in a field this save never touched is left as that till left it.
 */
public final class UnitPriceService {

    private final UnitPriceRepository repository;
    private final UnitPriceTransactionExecutor transactions;
    private final ChangeAnnouncer changeAnnouncer;

    public UnitPriceService(UnitPriceRepository repository) {
        this(repository, UnitPriceTransactionExecutor.jdbc(), ChangeAnnouncer.jdbc());
    }

    UnitPriceService(UnitPriceRepository repository, UnitPriceTransactionExecutor transactions,
                     ChangeAnnouncer changeAnnouncer) {
        this.repository = repository;
        this.transactions = transactions;
        this.changeAnnouncer = changeAnnouncer;
    }

    /**
     * Whether the reader may see what things cost. When not, every cost this service hands over is
     * zero - removing the column is what the items list does, and a figure that never crosses the
     * connection cannot be put back by a view menu.
     */
    public boolean costVisible() {
        return AuthorizationGuard.isGranted(AppPermissions.SHOW_COLUMN_BUY_PRICE);
    }

    /** Hints for the screen; {@link #save} asks again. */
    public boolean canEditUnits() {
        return AuthorizationGuard.isGranted(AppPermissions.ITEMS_UNIT_PRICE_UPDATE);
    }

    public boolean canEditItems() {
        return AuthorizationGuard.isGranted(AppPermissions.ITEMS_UPDATE);
    }

    public UnitPricePage page(UnitPriceFilter filter, int limit, int offset) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ITEMS_SHOW);
        if (limit <= 0 || offset < 0) throw new BusinessRuleException("unit.prices.error.page");
        boolean costVisible = costVisible();
        UnitPriceFilter safe = forReader(filter, costVisible);
        return new UnitPricePage(masked(repository.findPage(safe, limit, offset), costVisible),
                repository.count(safe), costVisible);
    }

    /** Every item the filter matches - what "all the items in this filter" covers. */
    public List<UnitPriceItem> all(UnitPriceFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ITEMS_SHOW);
        boolean costVisible = costVisible();
        return masked(repository.findAll(forReader(filter, costVisible)), costVisible);
    }

    public UnitPriceSaveResult save(UnitPriceSaveCommand command) throws DaoException {
        if (command == null || command.changes().isEmpty()) return new UnitPriceSaveResult(0, 0);
        if (command.touchesUnits()) AuthorizationGuard.require(AppPermissions.ITEMS_UNIT_PRICE_UPDATE);
        if (command.touchesItems()) AuthorizationGuard.require(AppPermissions.ITEMS_UPDATE);
        if (command.touchesCost()) AuthorizationGuard.require(AppPermissions.SHOW_COLUMN_BUY_PRICE);
        for (PriceChange change : command.changes()) {
            if (UnitPricePolicy.rangeRejection(change) != null) {
                throw new BusinessRuleException("unit.prices.error.range");
            }
        }

        return transactions.execute(() -> {
            Set<Integer> itemIds = new LinkedHashSet<>();
            command.changes().forEach(change -> itemIds.add(change.itemId()));
            Map<Integer, UnitPriceItem> stored = repository.lockItems(itemIds);

            Map<Integer, UnitPriceItem> after = new LinkedHashMap<>(stored);
            Set<Integer> itemsChanged = new HashSet<>();
            Map<Integer, Set<Integer>> unitsChanged = new LinkedHashMap<>();
            for (PriceChange change : command.changes()) {
                UnitPriceItem item = after.get(change.itemId());
                if (item == null) throw new BusinessRuleException("unit.prices.error.concurrent");
                if (change.isItem()) {
                    after.put(item.id(), item.withPrices(merged(item.prices(), change)));
                    itemsChanged.add(item.id());
                } else {
                    UnitPriceLine unit = item.unit(change.unitId());
                    if (unit == null) throw new BusinessRuleException("unit.prices.error.concurrent");
                    after.put(item.id(), replaceUnit(item, unit.withOwn(merged(unit.own(), change))));
                    unitsChanged.computeIfAbsent(item.id(), id -> new HashSet<>()).add(unit.unitId());
                }
            }

            for (Integer itemId : itemIds) {
                UnitPricePolicy.Rejection rejection = UnitPricePolicy.check(after.get(itemId),
                        itemsChanged.contains(itemId), unitsChanged.getOrDefault(itemId, Set.of()));
                if (rejection != null) {
                    throw new BusinessRuleException(LanguageManager.getInstance()
                            .getString(rejection.key(), rejection.arguments().toArray()));
                }
            }

            int itemsWritten = 0;
            int unitsWritten = 0;
            for (PriceChange change : command.changes()) {
                UnitPriceItem item = after.get(change.itemId());
                int written = change.isItem()
                        ? repository.updateItemPrices(item.id(), item.prices(), command.userId())
                        : repository.updateUnitPrices(item.id(), change.unitId(),
                                item.unit(change.unitId()).own(), command.userId());
                // The rows are locked, so nothing can have removed one since it was read; a write
                // that lands nowhere is a defect, and a partial save is exactly what this refuses.
                if (written != 1) throw new BusinessRuleException("unit.prices.error.concurrent");
                if (change.isItem()) itemsWritten++;
                else unitsWritten++;
            }
            changeAnnouncer.announce(new ItemsChanged());
            return new UnitPriceSaveResult(itemsWritten, unitsWritten);
        });
    }

    /**
     * The stored prices with the change's fields laid over them - after checking that each of those
     * fields still holds what the screen read. Fields the change does not move are the stored ones.
     */
    private static Prices merged(Prices stored, PriceChange change) throws BusinessRuleException {
        Prices result = stored;
        for (PriceField field : change.changedFields()) {
            if (!PriceChange.same(stored.get(field), change.before().get(field))) {
                throw new BusinessRuleException("unit.prices.error.concurrent");
            }
            result = result.with(field, change.after().get(field));
        }
        return result;
    }

    private static UnitPriceItem replaceUnit(UnitPriceItem item, UnitPriceLine replacement) {
        List<UnitPriceLine> units = new ArrayList<>();
        for (UnitPriceLine unit : item.units()) {
            units.add(unit.unitId() == replacement.unitId() ? replacement : unit);
        }
        return item.withUnits(units);
    }

    /** "Below cost" is a question about costs, so a reader who may not see them cannot ask it. */
    private static UnitPriceFilter forReader(UnitPriceFilter filter, boolean costVisible) {
        UnitPriceFilter safe = filter == null ? UnitPriceFilter.EMPTY : filter;
        return costVisible ? safe : safe.withBelowCostOnly(false);
    }

    private static List<UnitPriceItem> masked(List<UnitPriceItem> items, boolean costVisible) {
        if (costVisible) return items;
        Set<PriceField> cost = EnumSet.of(PriceField.BUY);
        return items.stream()
                .map(item -> item.withPrices(item.prices().with(PriceField.BUY, 0))
                        .withUnits(item.units().stream()
                                .map(unit -> unit.withOwn(AutomaticPricing.apply(unit, item.prices(), cost,
                                        AutomaticPricing.Mode.AUTOMATIC)))
                                .toList()))
                .toList();
    }
}
