package com.hamza.account.authorization;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The section a permission is shown under in the roles screen.
 * <p>
 * <b>It used to be the first word of the key</b>, uppercased by
 * {@code AppPermissions.definition()} - which produced {@code TOTAL}, {@code SHOW}, {@code UPDATE},
 * {@code MAIN}, {@code SUB} and {@code SEL} as section names, and then
 * {@code UserPermissionController.categoryLabel} matched those against a {@code switch} over eight
 * words it had chosen independently. Four of the eight could never match anything the derivation
 * produces: it yields {@code PURCHASE} where the switch says {@code PURCHASES}, {@code SETTING}
 * where it says {@code SETTINGS}, {@code CUSTOMER}/{@code SUPPLIERS} where it says {@code PARTIES},
 * and {@code USERS}/{@code ROLES} where it says {@code SECURITY}. Two of those differ by a single
 * letter. So <b>134 of the 162 keys read "عام"</b> - measured on a real database, and visible the
 * moment the screen was opened: the treasury family named its section and the purchases family
 * beside it said "general".
 * <p>
 * The section is declared here instead, beside the prefixes that belong to it, and
 * {@code PermissionCatalogArchitectureTest} fails the build when a key matches no group, when it
 * matches two, when a group owns no key, or when a group's label is missing from any of the three
 * bundles. There is deliberately <b>no fallback that quietly absorbs a new key</b> - that is exactly
 * how the old arrangement stayed wrong for as long as it did.
 * <p>
 * The name is what goes into {@code auth_permission.module_key}, rewritten by
 * {@code JdbcRbacRepository.synchronizeCatalog} on every start-up, so changing a grouping needs no
 * migration and reaches an existing install the first time it runs the new build.
 */
public enum PermissionGroup {

    /** Selling, its returns, and the two lists over them. */
    SALES("user.category.sales", "sales.", "total.sales."),

    /** Buying, its returns, and the two lists over them. */
    PURCHASES("user.category.purchases", "purchase.", "total.purchase."),

    /**
     * The catalogue: the item, what it is filed under, what it is sold in, and what it costs.
     * {@code show.column.buy.price} is here rather than under reports - it is the items list's own
     * column, and the screen it hides it on is this one.
     */
    ITEMS("user.category.items", "items.", "main.group.", "sub.group.", "units.", "sel.price.",
            "show.column."),

    /** The offers (V85): a paid add-on of its own, so its keys are a section of their own. */
    OFFERS("user.category.offers", "offer."),

    /** Warehouses, their balances, the transfers between them and the counts over them. */
    STOCK("user.category.stock", "stock.", "inventory."),

    /**
     * Customers and suppliers, and the areas they are filed under. An area belongs to a party and
     * not to an item, which is the whole reason {@code area.show} exists (V35).
     */
    PARTIES("user.category.parties", "customer.", "suppliers.", "area."),

    /**
     * The tills, what moves between them, the owner's own money, and the currencies they are counted in
     * (V80): a currency is a question about cash before it is a question about anything else.
     */
    TREASURY("user.category.treasury", "treasury.", "currency."),

    EXPENSES("user.category.expenses", "expenses."),

    /** The people, their jobs, what they are paid and whether they were here. */
    EMPLOYEES("user.category.employees", "employee.", "employees.", "job.", "payroll.",
            "attendance.", "leave."),

    /**
     * A delegate's rule, his month and the ceiling on his discount.
     * <p>
     * {@code sales.discount.override} is claimed by name and not by a {@code sales.discount.} prefix:
     * a prefix that reaches inside another group's family is decided by which of the two is longer,
     * which is not something anyone should have to work out from the screen. It is here rather than
     * under sales because that is how it is granted - V73 gives it to whoever holds
     * {@code commission.rule.update}, deliberately not to whoever may sell.
     */
    DELEGATES("user.category.delegates", List.of("commission."), List.of("sales.discount.override")),

    /**
     * A drawer answered for by whoever is on it. {@code user.shift.manage} is here and not under
     * security: it is the administration of shifts, and it is only the older key spelling that puts
     * "user" in front of it.
     */
    SHIFTS("user.category.shifts", "shift.", "user.shift."),

    /** What may be read about the business as a whole, rather than done to it. */
    REPORTS("user.category.reports", "reports.", "invoice."),

    /** Who may sign in, what they may do, and the record of what was done. */
    SECURITY("user.category.security", "users.", "roles.", "audit."),

    /**
     * The program's own settings, its backups, and the two rules about writing into a period that
     * is over - {@code accounting.lock.*} and the older {@code update.data.before.month}.
     */
    SETTINGS("user.category.settings", "setting.", "company.", "backup.", "main.totals.",
            "accounting.lock.", "update.data.");

    /** Shown when a stored module names no group - an enabled row always has a current one. */
    public static final String UNKNOWN_LABEL_KEY = "user.category.general";

    private final String labelKey;
    private final List<String> prefixes;
    private final List<String> keys;

    PermissionGroup(String labelKey, String... prefixes) {
        this(labelKey, List.of(prefixes), List.of());
    }

    PermissionGroup(String labelKey, List<String> prefixes, List<String> keys) {
        this.labelKey = labelKey;
        this.prefixes = List.copyOf(prefixes);
        this.keys = List.copyOf(keys);
    }

    public String labelKey() {
        return labelKey;
    }

    public List<String> prefixes() {
        return prefixes;
    }

    /** Keys this group claims outright, whichever family's prefix they fall under. */
    public List<String> keys() {
        return keys;
    }

    /**
     * The group a key belongs to. <b>A group that names the key outright wins</b>; otherwise the
     * prefixes decide, and two prefixes claiming one key is a build failure rather than something
     * settled by their length - see {@code PermissionCatalogArchitectureTest}.
     */
    public static Optional<PermissionGroup> of(String permissionKey) {
        if (permissionKey == null || permissionKey.isBlank()) return Optional.empty();
        return Arrays.stream(values())
                .filter(group -> group.keys.contains(permissionKey))
                .findFirst()
                .or(() -> Arrays.stream(values())
                        .flatMap(group -> group.prefixes.stream()
                                .filter(permissionKey::startsWith)
                                .map(prefix -> new Match(group, prefix.length())))
                        .max(Comparator.comparingInt(Match::length))
                        .map(Match::group));
    }

    /**
     * Every group that claims a key, so a test can refuse one that two of them do. A key named
     * outright has exactly one owner by construction, which is the point of naming it.
     */
    public static List<PermissionGroup> allMatching(String permissionKey) {
        if (permissionKey == null) return List.of();
        List<PermissionGroup> named = Arrays.stream(values())
                .filter(group -> group.keys.contains(permissionKey))
                .toList();
        if (!named.isEmpty()) return named;
        return Arrays.stream(values())
                .filter(group -> group.prefixes.stream().anyMatch(permissionKey::startsWith))
                .toList();
    }

    /** The stored {@code module_key} back to a group, for a row read out of the database. */
    public static Optional<PermissionGroup> byName(String moduleKey) {
        if (moduleKey == null || moduleKey.isBlank()) return Optional.empty();
        return Arrays.stream(values()).filter(group -> group.name().equals(moduleKey)).findFirst();
    }

    /** The bundle key naming a stored module, falling back where the row predates this build. */
    public static String labelKeyFor(String moduleKey) {
        return byName(moduleKey).map(PermissionGroup::labelKey).orElse(UNKNOWN_LABEL_KEY);
    }

    private record Match(PermissionGroup group, int length) {
    }
}
