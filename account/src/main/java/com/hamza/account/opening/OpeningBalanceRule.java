package com.hamza.account.opening;

import com.hamza.account.delete.ReferenceCheck;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.List;
import java.util.regex.Pattern;

/**
 * When an opening balance stops being editable, for one kind of row.
 *
 * @param entityKey     the i18n bundle key for what it is, for the message: "الصنف"/"the
 *                      item", "العميل"/"the customer"
 * @param table         the table the balance lives in
 * @param correctionKey the i18n bundle key for what to do instead, once it is closed -
 *                      the sentence is the whole point of the refusal, because "you
 *                      cannot" without "do this instead" is where people reach for the
 *                      database directly
 * @param movements     the tables whose rows mean this row has moved. Not the same list
 *                      as {@code DeleteRegistry}'s: that one mirrors foreign keys that
 *                      would refuse a delete, this one names anything that makes the
 *                      opening balance history. A cascading key still counts here - an
 *                      item's stock-count lines go with the item when it is deleted, but
 *                      while the item exists they are movements against it
 */
public record OpeningBalanceRule(String entityKey, String table, String correctionKey,
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
            throw new IllegalArgumentException("No movements declared for " + entityKey);
        }
        movements = List.copyOf(movements);
    }

    /** What it is, resolved through the current language. */
    public String entity() {
        return LanguageManager.getInstance().getString(entityKey);
    }

    /** What to do instead, resolved through the current language. */
    public String correction() {
        return LanguageManager.getInstance().getString(correctionKey);
    }

    public static Builder forEntity(String entityKey, String table) {
        return new Builder(entityKey, table);
    }

    public static final class Builder {

        private final String entityKey;
        private final String table;
        private final java.util.List<ReferenceCheck> movements = new java.util.ArrayList<>();
        private String correctionKey = "";

        private Builder(String entityKey, String table) {
            this.entityKey = entityKey;
            this.table = table;
        }

        /** "rows of {@code table} whose {@code column} is this id are {@code labelKey}". */
        public Builder movedBy(String table, String column, String labelKey) {
            movements.add(new ReferenceCheck(table, column, labelKey));
            return this;
        }

        public Builder correctedBy(String sentenceKey) {
            this.correctionKey = sentenceKey;
            return this;
        }

        public OpeningBalanceRule build() {
            return new OpeningBalanceRule(entityKey, table, correctionKey, movements);
        }
    }
}
