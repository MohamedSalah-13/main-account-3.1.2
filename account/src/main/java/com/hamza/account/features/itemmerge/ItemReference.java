package com.hamza.account.features.itemmerge;

/**
 * One place in the schema that names an item.
 *
 * @param table  the table holding the reference
 * @param column the column holding {@code items.id}
 * @param action what a merge does with those rows
 */
public record ItemReference(String table, String column, MergeAction action) {

    public ItemReference {
        if (table == null || table.isBlank() || column == null || column.isBlank()) {
            throw new IllegalArgumentException("An item reference needs a table and a column");
        }
    }

    /** {@code table.column}, for a message and for the log's {@code table_name}. */
    public String qualified() {
        return table + "." + column;
    }
}
