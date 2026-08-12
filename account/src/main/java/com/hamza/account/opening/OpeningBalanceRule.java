package com.hamza.account.opening;

import com.hamza.account.delete.ReferenceCheck;

import java.util.List;
import java.util.regex.Pattern;

/**
 * When an opening balance stops being editable, for one kind of row.
 *
 * @param entity     what it is, for the message: "الصنف", "العميل"
 * @param table      the table the balance lives in
 * @param correction what to do instead, once it is closed - the sentence is the whole
 *                   point of the refusal, because "you cannot" without "do this
 *                   instead" is where people reach for the database directly
 * @param movements  the tables whose rows mean this row has moved. Not the same list
 *                   as {@code DeleteRegistry}'s: that one mirrors foreign keys that
 *                   would refuse a delete, this one names anything that makes the
 *                   opening balance history. A cascading key still counts here - an
 *                   item's stock-count lines go with the item when it is deleted, but
 *                   while the item exists they are movements against it
 */
public record OpeningBalanceRule(String entity, String table, String correction,
                                 List<ReferenceCheck> movements) {

    /** As in {@link ReferenceCheck}: the table name is concatenated into SQL. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** Every one of these tables names the column the same way. */
    public static final String BALANCE_COLUMN = "first_balance";

    public OpeningBalanceRule {
        if (!IDENTIFIER.matcher(table).matches()) {
            throw new IllegalArgumentException("Not a table name: " + table);
        }
        if (movements.isEmpty()) {
            // A rule with nothing to check would report every row as free to edit
            // forever, which is the behaviour it exists to end.
            throw new IllegalArgumentException("No movements declared for " + entity);
        }
        movements = List.copyOf(movements);
    }

    public static Builder forEntity(String entity, String table) {
        return new Builder(entity, table);
    }

    public static final class Builder {

        private final String entity;
        private final String table;
        private final java.util.List<ReferenceCheck> movements = new java.util.ArrayList<>();
        private String correction = "";

        private Builder(String entity, String table) {
            this.entity = entity;
            this.table = table;
        }

        /** "rows of {@code table} whose {@code column} is this id are {@code label}". */
        public Builder movedBy(String table, String column, String label) {
            movements.add(new ReferenceCheck(table, column, label));
            return this;
        }

        public Builder correctedBy(String sentence) {
            this.correction = sentence;
            return this;
        }

        public OpeningBalanceRule build() {
            return new OpeningBalanceRule(entity, table, correction, movements);
        }
    }
}
