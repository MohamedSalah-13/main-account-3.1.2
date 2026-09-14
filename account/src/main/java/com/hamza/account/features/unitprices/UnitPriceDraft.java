package com.hamza.account.features.unitprices;

import com.hamza.controlsfx.util.NumberUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The screen's unsaved edits: the prices as they were read, the prices as the operator has left
 * them, and the difference between the two as a save command.
 * <p>
 * Everything the screen decides about an edit is here rather than in a cell factory, so a test can
 * put it through without a JavaFX toolkit: what an empty cell means, which cells are changed, what
 * "make automatic" would do and what it did, and which rows a save carries.
 * <p>
 * <b>An edit back to the stored value is no edit.</b> Typing a price and then typing the old one
 * again leaves the row unmarked and out of the save - a row counted as changed for a figure that is
 * what it was would be refused as a conflict the moment somebody else touched it.
 */
public final class UnitPriceDraft {

    /** A unit of an item, as the target of "make automatic". */
    public record UnitRef(int itemId, int unitId) {
    }

    /** One figure "make automatic" would change, before and after, for the preview. */
    public record PreviewRow(int itemId, String itemName, String unitName, PriceField field,
                             double before, boolean beforeAutomatic, double after, boolean afterAutomatic) {
    }

    private final Map<Integer, UnitPriceItem> original = new LinkedHashMap<>();
    private final Map<Integer, UnitPriceItem> current = new LinkedHashMap<>();

    /** Replaces everything held, edits included. */
    public void load(List<UnitPriceItem> items) {
        original.clear();
        current.clear();
        for (UnitPriceItem item : items) {
            original.put(item.id(), item);
            current.put(item.id(), item);
        }
    }

    /** The items as they stand on screen, in the order they were read. */
    public List<UnitPriceItem> items() {
        return List.copyOf(current.values());
    }

    public UnitPriceItem item(int itemId) {
        return current.get(itemId);
    }

    /** Sets one of the item's own prices. The value is rounded to the cent, as the column stores it. */
    public void setItemPrice(int itemId, PriceField field, double value) {
        UnitPriceItem item = requireItem(itemId);
        current.put(itemId, item.withPrices(item.prices().with(field, money(value))));
    }

    /** Sets one of a unit's own prices; {@code 0} makes that field automatic. */
    public void setUnitPrice(int itemId, int unitId, PriceField field, double value) {
        UnitPriceItem item = requireItem(itemId);
        UnitPriceLine unit = requireUnit(item, unitId);
        replaceUnit(item, unit.withOwn(unit.own().with(field, money(value))));
    }

    /** What {@link #applyPricing} would change, without changing it. */
    public List<PreviewRow> preview(Collection<UnitRef> targets, Set<PriceField> fields, AutomaticPricing.Mode mode) {
        List<PreviewRow> rows = new ArrayList<>();
        for (UnitRef target : targets) {
            UnitPriceItem item = current.get(target.itemId());
            UnitPriceLine unit = item == null ? null : item.unit(target.unitId());
            if (unit == null) continue;
            UnitPriceLine after = unit.withOwn(AutomaticPricing.apply(unit, item.prices(), fields, mode));
            for (PriceField field : fields) {
                if (PriceChange.same(unit.own().get(field), after.own().get(field))) continue;
                rows.add(new PreviewRow(item.id(), item.name(), unit.unitName(), field,
                        unit.effective(field, item.prices()), unit.isAutomatic(field),
                        after.effective(field, item.prices()), after.isAutomatic(field)));
            }
        }
        return rows;
    }

    /** Applies {@code mode} to {@code fields} of every target, and answers how many units it changed. */
    public int applyPricing(Collection<UnitRef> targets, Set<PriceField> fields, AutomaticPricing.Mode mode) {
        int changed = 0;
        for (UnitRef target : targets) {
            UnitPriceItem item = current.get(target.itemId());
            UnitPriceLine unit = item == null ? null : item.unit(target.unitId());
            if (unit == null) continue;
            Prices next = AutomaticPricing.apply(unit, item.prices(), fields, mode);
            if (new PriceChange(item.id(), unit.unitId(), unit.own(), next).isEmpty()) continue;
            replaceUnit(item, unit.withOwn(next));
            changed++;
        }
        return changed;
    }

    /** Every unit of every item held - what "all of them" means for the loaded rows. */
    public List<UnitRef> allUnits() {
        List<UnitRef> refs = new ArrayList<>();
        for (UnitPriceItem item : current.values()) {
            for (UnitPriceLine unit : item.units()) refs.add(new UnitRef(item.id(), unit.unitId()));
        }
        return refs;
    }

    /** Whether this figure differs from what was read. {@code unitId} {@link PriceChange#ITEM} is the item. */
    public boolean isChanged(int itemId, int unitId, PriceField field) {
        Prices before = stored(original.get(itemId), unitId);
        Prices after = stored(current.get(itemId), unitId);
        return before != null && after != null && !PriceChange.same(before.get(field), after.get(field));
    }

    public boolean isChanged(int itemId, int unitId) {
        for (PriceField field : PriceField.values()) {
            if (isChanged(itemId, unitId, field)) return true;
        }
        return false;
    }

    public boolean hasChanges() {
        return !changes().isEmpty();
    }

    /** How many rows - items and units - a save would write. */
    public int changedRowCount() {
        return changes().size();
    }

    public UnitPriceSaveCommand command(int userId) {
        return new UnitPriceSaveCommand(changes(), userId);
    }

    /** Puts every figure back to what was read. */
    public void discard() {
        current.clear();
        current.putAll(original);
    }

    private List<PriceChange> changes() {
        List<PriceChange> changes = new ArrayList<>();
        for (UnitPriceItem before : original.values()) {
            UnitPriceItem after = current.get(before.id());
            PriceChange itemChange = new PriceChange(before.id(), PriceChange.ITEM, before.prices(), after.prices());
            if (!itemChange.isEmpty()) changes.add(itemChange);
            for (UnitPriceLine unit : before.units()) {
                UnitPriceLine edited = after.unit(unit.unitId());
                PriceChange unitChange = new PriceChange(before.id(), unit.unitId(), unit.own(),
                        edited == null ? unit.own() : edited.own());
                if (!unitChange.isEmpty()) changes.add(unitChange);
            }
        }
        return changes;
    }

    private static Prices stored(UnitPriceItem item, int unitId) {
        if (item == null) return null;
        if (unitId == PriceChange.ITEM) return item.prices();
        UnitPriceLine unit = item.unit(unitId);
        return unit == null ? null : unit.own();
    }

    private void replaceUnit(UnitPriceItem item, UnitPriceLine replacement) {
        List<UnitPriceLine> units = new ArrayList<>(item.units().size());
        for (UnitPriceLine unit : item.units()) {
            units.add(unit.unitId() == replacement.unitId() ? replacement : unit);
        }
        current.put(item.id(), item.withUnits(units));
    }

    private UnitPriceItem requireItem(int itemId) {
        UnitPriceItem item = current.get(itemId);
        if (item == null) throw new IllegalArgumentException("item " + itemId + " is not on this screen");
        return item;
    }

    private static UnitPriceLine requireUnit(UnitPriceItem item, int unitId) {
        UnitPriceLine unit = item.unit(unitId);
        if (unit == null) throw new IllegalArgumentException("unit " + unitId + " is not a unit of item " + item.id());
        return unit;
    }

    private static double money(double value) {
        return Double.isFinite(value) ? NumberUtils.roundToTwoDecimalPlaces(value) : value;
    }
}
