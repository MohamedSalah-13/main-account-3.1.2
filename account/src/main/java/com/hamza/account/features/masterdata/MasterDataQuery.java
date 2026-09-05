package com.hamza.account.features.masterdata;

/** Only enum-owned identifiers enter SQL; every user value is a bound parameter. */
public final class MasterDataQuery {
    public static final int PAGE_SIZE = 50;
    private MasterDataQuery() { }

    public static String searchSql(MasterDataKind kind) {
        return "SELECT " + kind.idColumn + " AS entry_id, " + kind.nameColumn + " AS entry_name, "
                + (kind == MasterDataKind.SUB ? "main_id" : "0") + " AS parent_id, "
                + (kind == MasterDataKind.UNIT ? "value_d" : "1") + " AS factor, "
                + contentCountSql(kind) + " AS content_count FROM " + kind.table
                + " WHERE " + kind.nameColumn + " LIKE ? ESCAPE '!'"
                + (kind == MasterDataKind.SUB ? " AND main_id = ?" : "")
                + " ORDER BY " + kind.nameColumn + ", " + kind.idColumn + " LIMIT ? OFFSET ?";
    }

    /** Indexed scalar aggregates count the immediate children without multiplying the paged rows. */
    public static String contentCountSql(MasterDataKind kind) {
        return switch (kind) {
            case MAIN -> "(SELECT COUNT(*) FROM sub_group child WHERE child.main_id = main_group.id)";
            case SUB -> "(SELECT COUNT(*) FROM items child WHERE child.sub_num = sub_group.id)";
            default -> "0";
        };
    }

    public static String emptyGroupsSql(MasterDataKind kind) {
        String children = switch (kind) {
            case MAIN -> "SELECT 1 FROM sub_group child WHERE child.main_id = main_group.id";
            case SUB -> "SELECT 1 FROM items child WHERE child.sub_num = sub_group.id";
            default -> throw new IllegalArgumentException("Only groups have counted contents");
        };
        return "SELECT COUNT(*) FROM " + kind.table + " WHERE NOT EXISTS (" + children + ")";
    }

    public static String duplicateSql(MasterDataKind kind) {
        // sub_group.name is globally UNIQUE in the existing schema, not unique per parent.
        return "SELECT COUNT(*) FROM " + kind.table + " WHERE " + kind.nameColumn + " = ? AND "
                + kind.idColumn + " <> ?";
    }

    public static String pattern(String text) {
        String value = text == null ? "" : text.strip();
        return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
