package com.hamza.account.delete;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.controlsfx.language.LanguageManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything that decides whether one kind of row may be deleted, in one place:
 * who is allowed to, which ids are never deletable, and what would still be
 * pointing at the row.
 * <p>
 * These three used to be written in whichever style the file had reached for -
 * an {@code IllegalArgumentException} in {@code UsersDao}, a {@code DaoException}
 * with an Arabic sentence in {@code UnitsDao}, an {@code if (id == 1)} in
 * {@code NameController}, and nothing at all in most - so what protected a row
 * depended on which of them you happened to read. A rule is a declaration; the
 * checking is {@link DeletionService}'s, once, for all of them.
 * <p>
 * Rules live in {@link DeleteRegistry}. Adding an entity is one declaration
 * there, not a new branch in a delete method.
 */
public final class DeleteRule {

    private final String entityLabelKey;
    private final PermissionKey permission;
    private final Map<Integer, String> protectedIds;
    private final List<ReferenceCheck> references;

    private DeleteRule(Builder builder) {
        this.entityLabelKey = builder.entityLabelKey;
        this.permission = builder.permission;
        this.protectedIds = Map.copyOf(builder.protectedIds);
        this.references = List.copyOf(builder.references);
    }

    /**
     * @param entityLabelKey the i18n bundle key for what the row is called when the
     *                       user is told about it - "الوحدة"/"the unit", "الصنف"/"the
     *                       item". It goes into every message the rule produces, so
     *                       it reads as a noun, not a table name.
     */
    public static Builder forEntity(@NotNull String entityLabelKey) {
        return new Builder(entityLabelKey);
    }

    public String entityLabel() {
        return LanguageManager.getInstance().getString(entityLabelKey);
    }

    public PermissionKey permission() {
        return permission;
    }

    public List<ReferenceCheck> references() {
        return references;
    }

    /** The reason this id may never be deleted, or null when it may. */
    public String protectionFor(int id) {
        String reasonKey = protectedIds.get(id);
        return reasonKey == null ? null : LanguageManager.getInstance().getString(reasonKey);
    }

    public static final class Builder {

        private final String entityLabelKey;
        private final Map<Integer, String> protectedIds = new LinkedHashMap<>();
        private final List<ReferenceCheck> references = new ArrayList<>();
        private PermissionKey permission;

        private Builder(String entityLabelKey) {
            this.entityLabelKey = entityLabelKey;
        }

        /**
         * The permission the signed-in user needs. Left unset, the rule asks for
         * none - which is the honest declaration for a row that has no permission of
         * its own, rather than a check nobody wrote down.
         */
        public Builder requirePermission(PermissionKey permission) {
            this.permission = permission;
            return this;
        }

        /**
         * An id the application refuses to delete whatever points at it, and why.
         * These are the seeded rows the schema leans on: the DEFAULT behind a column,
         * the cash customer, the main treasury.
         */
        public Builder protectId(int id, @NotNull String reasonKey) {
            protectedIds.put(id, reasonKey);
            return this;
        }

        /**
         * A table whose rows would keep this one alive. One call per foreign key
         * that refuses the delete - see {@link ReferenceCheck} for which ones to
         * leave out.
         */
        public Builder referencedBy(@NotNull String table, @NotNull String column, @NotNull String labelKey) {
            references.add(new ReferenceCheck(table, column, labelKey));
            return this;
        }

        public DeleteRule build() {
            return new DeleteRule(this);
        }
    }
}
