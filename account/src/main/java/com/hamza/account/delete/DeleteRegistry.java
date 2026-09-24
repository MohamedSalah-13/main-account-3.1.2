package com.hamza.account.delete;

import com.hamza.account.config.DefaultStock;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;

/**
 * The delete rules, one per kind of row.
 * <p>
 * Every {@code referencedBy} below mirrors a foreign key in the schema that has
 * no {@code ON DELETE CASCADE}, so the database would refuse these deletes with
 * or without the rule - what the rule adds is which table and how many rows,
 * instead of the single "لا يمكن الحذف" that a constraint violation used to be
 * translated into. Keys that do cascade are left out on purpose: an item's units,
 * barcodes, stock rows and package rows go with the item, and counting them would
 * block a delete that is supposed to take them along.
 * <p>
 * Adding an entity is one declaration here.
 */
public final class DeleteRegistry {

    /**
     * Unit 1 is the DEFAULT on the {@code type} column of all four invoice tables
     * and on {@code stock_movements}, so a line written without a unit still
     * resolves to a row that exists.
     */
    public static final DeleteRule UNITS = DeleteRule.forEntity("delete.entity.unit")
            .requirePermission(AppPermissions.UNITS_DELETE)
            .protectId(1, "delete.protect.unit.default")
            .referencedBy("items", "unit_id", "delete.ref.item")
            .referencedBy("items_units", "unit", "delete.ref.item_unit")
            .referencedBy("sales", "type", "delete.ref.sales_line")
            .referencedBy("sales_re", "type", "delete.ref.sales_return_line")
            .referencedBy("purchase", "type", "delete.ref.purchase_line")
            .referencedBy("purchase_re", "type", "delete.ref.purchase_return_line")
            .referencedBy("stock_movements", "unit_id", "delete.ref.stock_movement")
            // Found by DeleteRegistryTest the day it was written: a counted line
            // carries the unit it was counted in (V8) and that key does not cascade,
            // so deleting a unit used on a stock count reached the database as a raw
            // SQL error instead of a refusal with a reason.
            .referencedBy("stock_count_lines", "unit_id", "delete.ref.stock_count")
            // V85: an offer for a unit ("5 off a carton"), and a target naming an item in one unit.
            .referencedBy("offer", "unit_id", "delete.ref.offer")
            .referencedBy("offer_target", "unit_id", "delete.ref.offer")
            .build();

    public static final DeleteRule ITEMS = DeleteRule.forEntity("delete.entity.item")
            .requirePermission(AppPermissions.ITEMS_DELETE)
            .referencedBy("sales", "num", "delete.ref.sales_line")
            .referencedBy("sales_re", "item_id", "delete.ref.sales_return_line")
            .referencedBy("purchase", "num", "delete.ref.purchase_line")
            .referencedBy("purchase_re", "item_id", "delete.ref.purchase_return_line")
            .referencedBy("stock_movements", "item_id", "delete.ref.stock_movement")
            .referencedBy("stock_transfer_list", "item_id", "delete.ref.stock_transfer_line")
            // V75 stopped this key cascading: a posted count sheet is a correction that was
            // actually made, and deleting the item used to take its lines out of one with no
            // refusal and no trace. Declarable only because it no longer cascades.
            .referencedBy("stock_count_lines", "item_id", "delete.ref.stock_count_line")
            // V85: an offer naming the item. Refused rather than cascaded: an offer that quietly lost its
            // only target would stay on screen reaching nothing.
            .referencedBy("offer_target", "item_id", "delete.ref.offer")
            .build();

    /** Customer 1 is "بيع نقدى", which the sales screen falls back to. */
    public static final DeleteRule CUSTOMERS = DeleteRule.forEntity("delete.entity.customer")
            .requirePermission(AppPermissions.CUSTOMER_DELETE)
            .protectId(1, "delete.protect.customer.cash")
            .referencedBy("total_sales", "sup_code", "delete.ref.sales_invoice")
            .referencedBy("total_sales_re", "sup_id", "delete.ref.sales_return")
            .referencedBy("customers_accounts", "account_code", "delete.ref.account_movement")
            .build();

    /** Supplier 1 is the seeded "مورد عام". */
    public static final DeleteRule SUPPLIERS = DeleteRule.forEntity("delete.entity.supplier")
            .requirePermission(AppPermissions.SUPPLIERS_DELETE)
            .protectId(1, "delete.protect.supplier.general")
            .referencedBy("total_buy", "sup_code", "delete.ref.purchase_invoice")
            .referencedBy("total_buy_re", "sup_id", "delete.ref.purchase_return")
            .referencedBy("suppliers_accounts", "account_code", "delete.ref.account_movement")
            .build();

    /** Treasury 1 is the seeded "الخزينة الرئيسية" and the DEFAULT behind every treasury_id. */
    public static final DeleteRule TREASURIES = DeleteRule.forEntity("delete.entity.treasury")
            .requirePermission(AppPermissions.TREASURY_DELETE)
            .protectId(1, "delete.protect.treasury.main")
            .referencedBy("total_sales", "treasury_id", "delete.ref.sales_invoice")
            .referencedBy("total_sales_re", "treasury_id", "delete.ref.sales_return")
            .referencedBy("total_buy", "treasury_id", "delete.ref.purchase_invoice")
            .referencedBy("total_buy_re", "treasury_id", "delete.ref.purchase_return")
            .referencedBy("customers_accounts", "treasury_id", "delete.ref.customer_account_movement")
            .referencedBy("suppliers_accounts", "treasury_id", "delete.ref.supplier_account_movement")
            .referencedBy("expenses_details", "treasury_id", "delete.ref.expense")
            .referencedBy("expense_recurring", "treasury_id", "delete.ref.expense.recurring")
            .referencedBy("treasury_deposit_expenses", "treasury_id", "delete.ref.deposit_or_withdrawal")
            .referencedBy("treasury_transfers", "treasury_from", "delete.ref.transfer_out")
            .referencedBy("treasury_transfers", "treasury_to", "delete.ref.transfer_in")
            // Declared although the table is empty: its key is not ON DELETE CASCADE, so
            // the day anything writes a movement, an undeclared reference stops being a
            // clean refusal and becomes a raw SQL error on a user's screen. Adding the
            // line now costs one query on a delete; adding it later costs remembering.
            .referencedBy("treasury_movements", "treasury_id", "delete.ref.treasury_movement")
            // A shift is opened on a till (V22). Its key is not ON DELETE CASCADE and
            // must not be: the row is a cashier's record of a day's cash, and it does
            // not stop being one because the drawer was later retired.
            .referencedBy("user_shifts", "treasury_id", "delete.ref.user_shift")
            .build();

    public static final DeleteRule MAIN_GROUPS = DeleteRule.forEntity("delete.entity.main_group")
            .requirePermission(AppPermissions.MAIN_GROUP_DELETE)
            .protectId(1, "delete.protect.main_group.default")
            .referencedBy("sub_group", "main_id", "delete.ref.sub_group")
            .referencedBy("offer_target", "main_group_id", "delete.ref.offer")
            .build();

    public static final DeleteRule SUB_GROUPS = DeleteRule.forEntity("delete.entity.sub_group")
            .requirePermission(AppPermissions.SUB_GROUP_DELETE)
            .protectId(1, "delete.protect.sub_group.default")
            .referencedBy("items", "sub_num", "delete.ref.item")
            .referencedBy("offer_target", "sub_group_id", "delete.ref.offer")
            .build();

    /**
     * An offer (V85). One a sale or a return line names is history and is stopped, never deleted
     * (docs/pricing-and-offers-plan.md ق-ع٧) - neither key cascades. Its targets and tiers are part of its
     * definition and go with it.
     */
    public static final DeleteRule OFFERS = DeleteRule.forEntity("delete.entity.offer")
            .requirePermission(AppPermissions.OFFER_DELETE)
            .referencedBy("sales", "offer_id", "delete.ref.sales_line")
            .referencedBy("sales_re", "offer_id", "delete.ref.sales_return_line")
            .build();

    /**
     * A job, which became a deletable row in V57.
     * <p>
     * Nothing is protected by id here, and that is deliberate. The four seeded jobs were
     * matched to a Java enum by hand, so none of them could be renamed or removed; now the
     * only thing holding a job is somebody doing it, which is what the reference below says.
     * A shop that sells nothing through delegates may delete "مندوب" once no employee holds
     * it - the rule the units screen arrived at for the same reason.
     */
    public static final DeleteRule JOBS = DeleteRule.forEntity("delete.entity.job")
            .requirePermission(AppPermissions.JOB_DELETE)
            .referencedBy("employees", "job", "delete.ref.employee")
            .build();

    /** Employee 1 is the seeded "بيع مباشر" delegate. */
    public static final DeleteRule EMPLOYEES = DeleteRule.forEntity("delete.entity.employee")
            .requirePermission(AppPermissions.EMPLOYEE_DELETE)
            .protectId(1, "delete.protect.employee.direct_sale")
            .referencedBy("total_sales", "delegate_id", "delete.ref.sales_invoice")
            .referencedBy("total_sales_re", "delegate_id", "delete.ref.sales_return")
            // A collection this delegate made (V71). A fact like the invoice above it, not a
            // preference like custom.default_delegate_id, which V56 left without a key on purpose.
            .referencedBy("customers_accounts", "delegate_id", "delete.ref.customer_account_movement")
            // A month's approved commission (V72): frozen, and his whether or not it was paid.
            .referencedBy("commission_line", "employee_id", "delete.ref.commission.line")
            // The real link, and the one the application writes (V23). Until then the
            // only declared reference was expense_salary below, which nothing has
            // ever written to: it blocked the deletion of the one employee its three
            // legacy rows happen to name and let every other one through, with years
            // of salaries behind them.
            .referencedBy("expenses_details", "emp_id", "delete.ref.expense")
            // What the employee earned, was awarded and was deducted (V58). Not cascading on
            // purpose: a financial history is not something swept away with the row it belongs
            // to, and an employee with one movement is one whose account somebody can still be
            // asked to explain.
            .referencedBy("employee_ledger", "employee_id", "delete.ref.employee.ledger")
            // A payroll line is the record that this employee was paid for that month, and
            // an approved run is frozen - so it holds the employee the way an invoice does.
            // It does not cascade from the employee (it cascades from its own run), which is
            // exactly the distinction this catalog declares.
            .referencedBy("payroll_line", "employee_id", "delete.ref.payroll.line")
            .referencedBy("employee_allowance", "employee_id", "delete.ref.employee.allowance")
            // The grid and the leave requests are this employee's own record of what each day
            // was; neither cascades from the employee, which is what this catalog declares.
            .referencedBy("attendance", "employee_id", "delete.ref.attendance")
            .referencedBy("leave_request", "employee_id", "delete.ref.leave.request")
            // Kept: the table still holds those three rows and its key still refuses
            // a delete. Nothing reads it for a decision any more - see V23.
            .referencedBy("expense_salary", "employee_id", "delete.ref.salary")
            // targeted_sales.delegate_id is ON DELETE CASCADE - a delegate's targets
            // go with the delegate - so it is not something that holds them back.
            .build();

    /**
     * The expense line itself, not the expense type. Nothing holds it: the one
     * table pointing at {@code expenses_details} is {@code expense_salary}, and
     * that key is {@code ON DELETE CASCADE}, so the salary row goes with the
     * expense. Declaring it as a reference would refuse a delete the database
     * performs happily - which is why only the non-cascading keys belong here.
     */
    public static final DeleteRule EXPENSES_DETAILS = DeleteRule.forEntity("delete.entity.expense")
            .requirePermission(AppPermissions.EXPENSES_DELETE)
            .build();

    /**
     * A heading expenses are filed under (V64). Nothing is protected by id: a heading the system
     * depends on is found by its {@code system_key}, and {@code ExpenseHeadingRules.requireDeletable}
     * refuses that one before this rule is reached - a protected id would be the number V21 happened
     * to give the wallet-fee heading on one install and a different one on the next.
     */
    public static final DeleteRule EXPENSE_HEADINGS = DeleteRule.forEntity("delete.entity.expense.heading")
            .requirePermission(AppPermissions.EXPENSES_HEADINGS_UPDATE)
            .referencedBy("expenses_details", "type_code", "delete.ref.expense")
            .referencedBy("expenses", "parent_id", "delete.ref.expense.heading")
            // V66. Neither key cascades, so MySQL refuses the delete either way; declared here so the
            // refusal reads as a sentence with a count rather than as a reference code.
            .referencedBy("expense_budget", "heading_id", "delete.ref.expense.budget")
            .referencedBy("expense_recurring", "heading_id", "delete.ref.expense.recurring")
            .build();

    /**
     * A currency (V80). Its rates are not {@code ON DELETE CASCADE} on purpose: a currency's rate history
     * is a record of what the shop traded at, and deleting the currency does not erase it with it - one
     * that is merely out of use is stopped instead, which is what {@code is_active} is for. The base
     * currency is refused before this rule is asked ({@code CurrencyRules.requireDeletable}), because it
     * is a property of the whole database and not a row anything points at.
     */
    public static final DeleteRule CURRENCIES = DeleteRule.forEntity("delete.entity.currency")
            .requirePermission(AppPermissions.CURRENCY_UPDATE)
            .referencedBy("currency_rate", "currency_id", "delete.ref.currency.rate")
            // V81: a treasury in the currency. Not cascading - a treasury's history is in its currency.
            .referencedBy("treasury", "currency_id", "delete.ref.currency.treasury")
            // V82: a customer or a supplier dealing in it - every amount on their account is in it.
            .referencedBy("custom", "currency_id", "delete.ref.currency.customer")
            .referencedBy("suppliers", "currency_id", "delete.ref.currency.supplier")
            // V83: a document written in it - its prices and its cash were typed in it.
            .referencedBy("total_sales", "currency_id", "delete.ref.sales_invoice")
            .referencedBy("total_sales_re", "currency_id", "delete.ref.sales_return")
            .referencedBy("total_buy", "currency_id", "delete.ref.purchase_invoice")
            .referencedBy("total_buy_re", "currency_id", "delete.ref.purchase_return")
            .build();

    /**
     * Stock {@code DefaultStock.ID} is the seeded {@code 'الرئيسي'} row every document
     * still writes to; see {@link DefaultStock}. {@code items_stock}, the four invoice
     * totals tables and {@code stock_count} all carry a non-cascading {@code stock_id},
     * and {@code stock_transfer} carries two.
     */
    public static final DeleteRule STOCKS = DeleteRule.forEntity("delete.entity.stock")
            .requirePermission(AppPermissions.STOCK_DELETE)
            .protectId(DefaultStock.ID, "delete.protect.stock.default")
            .referencedBy("items_stock", "stock_id", "delete.ref.item_stock")
            .referencedBy("stock_movements", "stock_id", "delete.ref.stock_movement")
            .referencedBy("total_sales", "stock_id", "delete.ref.sales_invoice")
            .referencedBy("total_sales_re", "stock_id", "delete.ref.sales_return")
            .referencedBy("total_buy", "stock_id", "delete.ref.purchase_invoice")
            .referencedBy("total_buy_re", "stock_id", "delete.ref.purchase_return")
            .referencedBy("stock_transfer", "stock_from", "delete.ref.transfer_out")
            .referencedBy("stock_transfer", "stock_to", "delete.ref.transfer_in")
            .referencedBy("stock_count", "stock_id", "delete.ref.stock_count")
            .build();

    /**
     * The transfer header. {@code stock_transfer_list} cascades with it - see
     * {@code V1__baseline.sql} - a transfer's lines have no meaning without the
     * transfer, unlike an invoice line, which is itself the record of a sale.
     */
    public static final DeleteRule STOCK_TRANSFERS = DeleteRule.forEntity("delete.entity.stock_transfer")
            .requirePermission(AppPermissions.STOCK_TRANSFER_DELETE)
            .build();

    /**
     * The rule for whichever side of the ledger a name belongs to.
     * <p>
     * The name and account screens are one set of generic controllers serving both
     * sides, so they select the rule the same way they select the event to publish
     * - from the {@code PartyKind} the implementation answers with - rather than
     * each of the four {@code DataInterface} implementations carrying a rule of
     * its own.
     */
    public static DeleteRule forParty(PartyKind kind) {
        return kind == PartyKind.SUPPLIER ? SUPPLIERS : CUSTOMERS;
    }

    private DeleteRegistry() {
    }
}
