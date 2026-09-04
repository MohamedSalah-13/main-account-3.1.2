package com.hamza.account.wipe;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What the "delete data" screen can erase, declared.
 * <p>
 * This replaced {@code truncateTableSales}, {@code truncateTablePurchase},
 * {@code truncateTableItems} and {@code truncateTableOthers} - four stored
 * procedures taking sixteen booleans between them, whose meaning was a chain of
 * {@code IF (flag) THEN TRUNCATE} that only the SQL knew, and whose ordering
 * problem was solved by turning foreign-key checking off for the duration. What
 * they erased, what they put back, and what had to go with what, are now three
 * fields on a record.
 * <p>
 * <b>The order of this list is the order the tables are emptied in</b>, and it is
 * not arbitrary: a table may only be emptied once everything pointing at it has
 * been. That is what lets the wipe run as ordinary DELETEs inside one
 * transaction, with the foreign keys left switched on to catch a mistake rather
 * than switched off to hide one.
 */
public final class WipeCatalog {

    // ---- invoices ------------------------------------------------------------
    // Lines go before headers even though the key cascades: naming both keeps the
    // plan readable, and a cascade the schema might lose does not change what the
    // wipe erases.
    //
    // document_sequences is deliberately not a wipe target. Invoice numbers are
    // business identifiers and must never be reused merely because invoice rows
    // were removed from this administrative screen.

    public static final WipeTarget SALES_RETURNS = WipeTarget.of("salesReturns", "wipe.target.salesReturns",
            List.of(WipeTable.of("sales_re"), WipeTable.of("total_sales_re")));

    /**
     * Requires the returns, and not because of a foreign key - {@code
     * total_sales_re.source_invoice_number} is {@code ON DELETE SET NULL}, so the
     * database would empty this happily and leave the returns standing.
     * <p>
     * It is the <em>stock</em> that breaks. A sale takes goods out and its return puts
     * them back; erasing only the sale leaves the return's stock-in with nothing to
     * reverse, and every returned item's balance rises by the returned quantity out of
     * nothing. The two are one movement in opposite directions and have to go together.
     */
    public static final WipeTarget SALES = WipeTarget.of("sales", "wipe.target.sales",
            List.of(WipeTable.of("sales"), WipeTable.of("total_sales")),
            "salesReturns");

    public static final WipeTarget PURCHASE_RETURNS = WipeTarget.of("purchaseReturns", "wipe.target.purchaseReturns",
            List.of(WipeTable.of("purchase_re"), WipeTable.of("total_buy_re")));

    /** Requires its returns for the same reason {@link #SALES} does, mirrored. */
    public static final WipeTarget PURCHASES = WipeTarget.of("purchases", "wipe.target.purchases",
            List.of(WipeTable.of("purchase"), WipeTable.of("total_buy")),
            "purchaseReturns");

    // ---- names and their accounts --------------------------------------------

    public static final WipeTarget CUSTOMER_ACCOUNTS = WipeTarget.of("customerAccounts", "wipe.target.customerAccounts",
            List.of(WipeTable.of("customers_accounts")));

    public static final WipeTarget SUPPLIER_ACCOUNTS = WipeTarget.of("supplierAccounts", "wipe.target.supplierAccounts",
            List.of(WipeTable.of("suppliers_accounts")));

    /** Customer 1 is the cash customer the sales screen falls back to. */
    public static final WipeTarget CUSTOMERS = WipeTarget.of("customers", "wipe.target.customers",
            List.of(WipeTable.of("custom",
                    "INSERT INTO custom(id, name, limit_num, price_id) VALUES (1, 'بيع نقدى', 5000, 1)")),
            "sales", "salesReturns", "customerAccounts");

    public static final WipeTarget SUPPLIERS = WipeTarget.of("suppliers", "wipe.target.suppliers",
            List.of(WipeTable.of("suppliers",
                    "INSERT INTO suppliers(id, name) VALUES (1, 'مورد عام')")),
            "purchases", "purchaseReturns", "supplierAccounts");

    // ---- items ---------------------------------------------------------------

    /**
     * {@code type_price} is deliberately not here, though the old procedure
     * truncated it. Customers point at their price tier
     * ({@code custom.price_id}), so emptying it would either drag the customers
     * into every wipe of the items or - as it did - leave them pointing at tiers
     * that no longer exist. The three tier names are configuration; erasing the
     * items now leaves them alone.
     * <p>
     * The stock-transfer tables are here rather than in a warehouse target of
     * their own: multi-warehouse support was removed, so nothing writes them any
     * more, but old rows still point at items and would refuse the delete.
     */
    /**
     * The physical counts (V8). Its lines point at {@code units}, which the items
     * target empties, so items requires this one and the lines go first.
     * <p>
     * Both tables are named although {@code count_id} cascades, for the same reason
     * the invoice targets name their lines: the plan reads as what it erases.
     */
    public static final WipeTarget STOCK_COUNTS = WipeTarget.of("stockCounts", "wipe.target.stockCounts",
            List.of(WipeTable.of("stock_count_lines"), WipeTable.of("stock_count")));

    /**
     * {@code item_merge} and its lines (V17) go with the items for the same reason the
     * counts do: the log names items by id and by the name they had, and an erased
     * catalogue leaves it describing rows nobody can look up. It has no foreign key to
     * {@code items} - deliberately, since the merged item is deleted by definition - so
     * nothing but this line would take it.
     */
    public static final WipeTarget ITEMS = WipeTarget.of("items", "wipe.target.items",
            List.of(WipeTable.of("item_merge_lines"),
                    WipeTable.of("item_merge"),
                    WipeTable.of("item_barcodes"),
                    WipeTable.of("items_package"),
                    WipeTable.of("items_units"),
                    WipeTable.of("items_stock"),
                    WipeTable.of("stock_movements"),
                    WipeTable.of("stock_transfer_list"),
                    WipeTable.of("stock_transfer"),
                    WipeTable.of("items"),
                    WipeTable.of("units",
                            "INSERT INTO units(unit_id, unit_name) VALUES (1, 'قطعة'), (2, 'كرتونة')")),
            "sales", "salesReturns", "purchases", "purchaseReturns", "stockCounts");

    public static final WipeTarget SUB_GROUPS = WipeTarget.of("subGroups", "wipe.target.subGroups",
            List.of(WipeTable.of("sub_group",
                    "INSERT INTO sub_group(id, name, main_id) VALUES (1, 'فرع 1', 1)")),
            "items");

    public static final WipeTarget MAIN_GROUPS = WipeTarget.of("mainGroups", "wipe.target.mainGroups",
            List.of(WipeTable.of("main_group",
                    "INSERT INTO main_group(id, name_g) VALUES (1, 'عام 1')")),
            "subGroups");

    // ---- the rest ------------------------------------------------------------

    public static final WipeTarget EXPENSES = WipeTarget.of("expenses", "wipe.target.expenses",
            List.of(WipeTable.of("expense_salary"), WipeTable.of("expenses_details")));

    /**
     * Employees and the treasury go together because the old procedure put them
     * together, and because the salary rows tie them: erasing the staff without
     * the money they were paid from leaves a treasury nobody can account for.
     * <p>
     * {@code treasury_movements} is emptied here although no procedure ever
     * touched it - it points at the treasury, so it had to be either erased or
     * left to refuse the delete.
     */
    public static final WipeTarget EMPLOYEES = WipeTarget.of("employees", "wipe.target.employees",
            List.of(WipeTable.of("targeted_sales"),
                    WipeTable.of("treasury_deposit_expenses"),
                    WipeTable.of("shift_cash_handover_receipts"),
                    WipeTable.of("treasury_transfers"),
                    WipeTable.of("treasury_movements"),
                    WipeTable.of("shift_close_decisions"),
                    WipeTable.of("shift_close_requests"),
                    WipeTable.of("shift_close_snapshots"),
                    WipeTable.of("shift_cash_ledger"),
                    WipeTable.of("shift_cash_handovers"),
                    WipeTable.of("shift_cash_handover_policy"),
                    WipeTable.of("cashier_treasury_assignment_events"),
                    WipeTable.of("cashier_treasury_assignment"),
                    // A shift is opened on a till since V22, and that key does not
                    // cascade - so the treasury below cannot be emptied while any
                    // shift still points at it. It belongs with this target rather
                    // than with the users one: a shift row is a day's cash, and the
                    // users wipe keeps user 1 and takes the rest by cascade.
                    WipeTable.of("user_shifts"),
                    WipeTable.of("employees",
                            "INSERT INTO employees (id, column_name, birth_date, hire_date, salary, job) "
                            + "VALUES (1, 'بيع مباشر', CURRENT_DATE(), CURRENT_DATE(), 0, 4)"),
                    WipeTable.of("treasury",
                            "INSERT INTO treasury(id, t_name, amount) VALUES (1, 'الخزينة الرئيسية', 0)")),
            "sales", "salesReturns", "purchases", "purchaseReturns",
            "customerAccounts", "supplierAccounts", "expenses");

    public static final WipeTarget PROCESSES = WipeTarget.of("processes", "wipe.target.processes",
            List.of(WipeTable.of("audit_log")));

    /**
     * User 1 survives, where the old procedure truncated the table and inserted it
     * back. Every {@code user_id} column in the schema points at {@code users},
     * including tables no wipe touches - {@code stocks} and {@code type_price}
     * among them - so emptying it outright is what made turning the foreign keys
     * off unavoidable. Keeping the row those columns point at, and resetting it to
     * the admin account, reaches the same place with the constraints left on.
     */
    public static final WipeTarget USERS = WipeTarget.of("users", "wipe.target.users",
            List.of(WipeTable.keeping("users", "id <> 1",
                            "UPDATE users SET user_name = 'admin', user_pass = 'admin', user_available = 1 WHERE id = 1")),
            "sales", "salesReturns", "purchases", "purchaseReturns",
            "customerAccounts", "supplierAccounts", "customers", "suppliers",
            "items", "subGroups", "mainGroups", "expenses", "employees");

    /** Declaration order is execution order - see the class comment. */
    public static final List<WipeTarget> TARGETS = List.of(
            SALES_RETURNS, SALES, PURCHASE_RETURNS, PURCHASES,
            CUSTOMER_ACCOUNTS, SUPPLIER_ACCOUNTS, CUSTOMERS, SUPPLIERS,
            STOCK_COUNTS, ITEMS, SUB_GROUPS, MAIN_GROUPS,
            EXPENSES, EMPLOYEES, PROCESSES, USERS);

    private static final Map<String, WipeTarget> BY_ID =
            TARGETS.stream().collect(Collectors.toMap(WipeTarget::id, Function.identity()));

    private WipeCatalog() {
    }

    public static Optional<WipeTarget> byId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /**
     * Everything that has to go when these targets go: the targets asked for, plus
     * whatever their {@code requires} pull in, and whatever those pull in.
     * <p>
     * The screen uses it to tick the boxes the user did not, and the plan uses it
     * so a wipe cannot be run with half of what it depends on left behind.
     */
    public static List<WipeTarget> closureOf(Collection<WipeTarget> selected) {
        Set<String> resolved = new LinkedHashSet<>();
        Deque<String> pending = selected.stream()
                .map(WipeTarget::id)
                .collect(Collectors.toCollection(ArrayDeque::new));

        while (!pending.isEmpty()) {
            String id = pending.poll();
            if (!resolved.add(id)) {
                continue;
            }
            byId(id).orElseThrow(() -> new IllegalStateException("Unknown wipe target: " + id))
                    .requires()
                    .forEach(pending::add);
        }

        // Rebuilt from TARGETS rather than from the walk, so the answer is always in
        // execution order however the caller happened to ask. A List, not a Set: the
        // order is the point, and Set.copyOf in a caller would quietly lose it.
        return TARGETS.stream()
                .filter(target -> resolved.contains(target.id()))
                .toList();
    }
}
