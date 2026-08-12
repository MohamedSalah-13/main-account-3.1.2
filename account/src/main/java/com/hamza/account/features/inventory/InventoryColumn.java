package com.hamza.account.features.inventory;

import java.util.List;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * One column of the inventory sheet, described rather than built.
 * <p>
 * Nothing here touches JavaFX, which is the point twice over: the catalogue in
 * {@link InventoryColumns} can be checked by a test with no toolkit running, and the
 * screen holds one small piece of code that turns a description into a
 * {@code TableColumn} instead of a block per column.
 * <p>
 * What this replaced was eight near-identical blocks followed by
 * {@code getColumns().removeAll(List.of(5, 4, 3, 1))} - columns deleted by position,
 * so adding an annotated field to the item model removed different ones and nobody
 * found out until a customer noticed a missing column.
 *
 * @param id      stable identifier; {@code TableSetting} remembers width and
 *                visibility against it, so renaming one forgets the user's layout
 *                while reordering the catalogue no longer does
 * @param text    reads the cell for a {@link ColumnKind#TEXT} column, else null
 * @param number  reads the cell for a numeric column, else null
 * @param children sub-columns for a heading such as شراء (سعر, إجمالى); empty for a
 *                leaf. A parent carries no reader of its own
 */
public record InventoryColumn(
        String id,
        String title,
        ColumnKind kind,
        Function<InventoryRow, String> text,
        ToDoubleFunction<InventoryRow> number,
        double prefWidth,
        List<InventoryColumn> children) {

    public InventoryColumn {
        children = children == null ? List.of() : List.copyOf(children);
        boolean group = !children.isEmpty();
        boolean hasReader = text != null || number != null;
        if (group && hasReader) {
            throw new IllegalArgumentException("A heading column reads nothing of its own: " + id);
        }
        if (!group && !hasReader) {
            throw new IllegalArgumentException("A leaf column needs a reader: " + id);
        }
        if (kind == ColumnKind.TEXT && number != null) {
            throw new IllegalArgumentException("A text column is read as text: " + id);
        }
        if (kind != ColumnKind.TEXT && text != null) {
            throw new IllegalArgumentException("A numeric column is read as a number: " + id);
        }
    }

    public static InventoryColumn text(String id, String title,
                                       Function<InventoryRow, String> reader, double width) {
        return new InventoryColumn(id, title, ColumnKind.TEXT, reader, null, width, List.of());
    }

    public static InventoryColumn quantity(String id, String title, ToDoubleFunction<InventoryRow> reader) {
        return new InventoryColumn(id, title, ColumnKind.QUANTITY, null, reader, 110, List.of());
    }

    public static InventoryColumn money(String id, String title, ToDoubleFunction<InventoryRow> reader) {
        return new InventoryColumn(id, title, ColumnKind.MONEY, null, reader, 110, List.of());
    }

    /**
     * A heading over other columns - price and total under شراء, say. Built here
     * rather than through {@code AddColumnMix}, which takes its sub-columns from a
     * {@code HashMap} and so cannot say which of the two comes first.
     */
    public static InventoryColumn group(String id, String title, InventoryColumn... children) {
        return new InventoryColumn(id, title, ColumnKind.TEXT, null, null, 0, List.of(children));
    }

    public boolean isGroup() {
        return !children.isEmpty();
    }

    public double numberOf(InventoryRow row) {
        return number.applyAsDouble(row);
    }

    public String textOf(InventoryRow row) {
        return text.apply(row);
    }

    /** The cell exactly as it is written on screen and in an export. */
    public String render(InventoryRow row) {
        if (isGroup()) {
            return "";
        }
        return kind == ColumnKind.TEXT ? textOf(row) : kind.format(numberOf(row));
    }

    /** This column and, for a heading, the columns under it. */
    public List<InventoryColumn> leaves() {
        return isGroup() ? children : List.of(this);
    }
}
