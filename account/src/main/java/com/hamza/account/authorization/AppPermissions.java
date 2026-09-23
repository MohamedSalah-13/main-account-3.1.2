package com.hamza.account.authorization;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Application permission catalogue. Adding a permission is one constant; metadata is derived and
 * synchronized automatically. No database id, switch statement, or permission screen edit is needed.
 * <p>
 * <b>A key declared here must be read by something.</b> Eight were not - four of the settings tabs,
 * both of the price-tier ones, a treasury balance and the read half of the month rule - so they
 * appeared in the roles screen as tick boxes that changed nothing a user could do, and a role built
 * out of them granted nothing. {@code PermissionCatalogArchitectureTest} fails the build on a ninth,
 * and its exemption list fails in both directions.
 * <p>
 * <b>Removing one is safe and needs no migration.</b> {@code JdbcRbacRepository.synchronizeCatalog}
 * disables every system permission and re-enables the ones declared here, so a key that goes leaves
 * its row behind with {@code enabled = 0}: it drops out of the screen and out of
 * {@code findEffectivePermissions}, and the grants that named it stay dormant rather than being
 * deleted from a customer's database. A key put back is re-enabled with those grants intact.
 * <p>
 * <b>The risk is derived from the key's last word, and is declared where that is wrong.</b>
 * {@link #risk(String)} reads the action - DELETE and MANAGE are {@code CRITICAL}, CREATE and UPDATE
 * {@code HIGH} - which is right for the four-verb families and silent for everything else: CAPITAL,
 * OPENING, TRANSFER, DEPOSIT, PAY, APPROVE, ADJUST and MERGE all fall through to {@code LOW}, and
 * two of those are the owner's money. Those keys pass their risk to {@link #key(String,
 * PermissionRisk)} beside the sentence that justifies it, rather than being listed a second time
 * somewhere below.
 */
public final class AppPermissions {

    /**
     * Risks named at the declaration, read back by {@link #definition}. Declared before the first
     * constant because a field initializer runs in declaration order, and {@code key(value, risk)}
     * writes to it.
     */
    private static final Map<String, PermissionRisk> DECLARED_RISKS = new HashMap<>();

    public static final PermissionKey PURCHASE_SHOW = key("purchase.show");
    public static final PermissionKey PURCHASE_CREATE = key("purchase.create");
    public static final PermissionKey PURCHASE_UPDATE = key("purchase.update");
    public static final PermissionKey PURCHASE_DELETE = key("purchase.delete");
    /** The quick screen for purchases - see {@link #SALES_QUICK}. */
    public static final PermissionKey PURCHASE_QUICK = key("purchase.quick");
    public static final PermissionKey TOTAL_PURCHASE_SHOW = key("total.purchase.show");
    public static final PermissionKey TOTAL_PURCHASE_SHOW_INVOICE = key("total.purchase.show.invoice");
    public static final PermissionKey PURCHASE_RE_SHOW = key("purchase.re.show");
    public static final PermissionKey PURCHASE_RE_CREATE = key("purchase.re.create");
    public static final PermissionKey PURCHASE_RE_UPDATE = key("purchase.re.update");
    public static final PermissionKey PURCHASE_RE_DELETE = key("purchase.re.delete");
    public static final PermissionKey TOTAL_PURCHASE_RE_SHOW = key("total.purchase.re.show");
    public static final PermissionKey TOTAL_PURCHASE_RE_SHOW_INVOICE = key("total.purchase.re.show.invoice");
    public static final PermissionKey SALES_SHOW = key("sales.show");
    public static final PermissionKey SALES_CREATE = key("sales.create");
    public static final PermissionKey SALES_UPDATE = key("sales.update");
    public static final PermissionKey SALES_DELETE = key("sales.delete");
    /**
     * The quick invoice screen, where the lines table is the only entry surface. It is not a
     * guard on writing - saving still asks {@link #SALES_CREATE} inside the save - but a choice of
     * screen the owner may give a cashier or keep from one. V79 grants it to whoever may create.
     */
    public static final PermissionKey SALES_QUICK = key("sales.quick");
    public static final PermissionKey TOTAL_SALES_SHOW = key("total.sales.show");
    public static final PermissionKey TOTAL_SALES_SHOW_INVOICE = key("total.sales.show.invoice");
    public static final PermissionKey SALES_RE_SHOW = key("sales.re.show");
    public static final PermissionKey SALES_RE_CREATE = key("sales.re.create");
    public static final PermissionKey SALES_RE_UPDATE = key("sales.re.update");
    public static final PermissionKey SALES_RE_DELETE = key("sales.re.delete");
    public static final PermissionKey TOTAL_SALES_RE_SHOW = key("total.sales.re.show");
    public static final PermissionKey TOTAL_SALES_RE_SHOW_INVOICE = key("total.sales.re.show.invoice");
    public static final PermissionKey ITEMS_SHOW = key("items.show");
    public static final PermissionKey ITEMS_CREATE = key("items.create");
    public static final PermissionKey ITEMS_UPDATE = key("items.update");
    public static final PermissionKey ITEMS_DELETE = key("items.delete");
    public static final PermissionKey ITEMS_ADD_EXCEL = key("items.add.excel");
    /** Reclassifying items between subgroups without granting every other item edit. */
    public static final PermissionKey ITEMS_GROUP_MOVE = key("items.group.move");
    /**
     * Setting the prices a unit carries of its own, or clearing them so the unit is priced from
     * the item. Narrower than {@link #ITEMS_UPDATE}, which also edits the item's own prices on the
     * same screen; V62 grants it to every role that already held that.
     */
    public static final PermissionKey ITEMS_UNIT_PRICE_UPDATE = key("items.unit.price.update");
    /**
     * Folding one item into another and deleting it. Held with {@link #ITEMS_DELETE}, never instead
     * of it - so it carries that key's {@code CRITICAL} rather than the {@code LOW} MERGE derives.
     * It repoints every line the item ever appeared on and then deletes the row.
     */
    public static final PermissionKey ITEMS_MERGE = key("items.merge", PermissionRisk.CRITICAL);
    /**
     * Opening the price-check screen - the one that hangs on the shop wall and answers a
     * customer's barcode with a price.
     * <p>
     * Deliberately not {@link #ITEMS_SHOW}: that key opens the item list, which carries the
     * buying price and the value of the stock. A screen standing unattended in front of
     * customers is exactly where those must not be, so the two are different abilities and
     * a shop can grant one without the other. V36 grants it to every role that could
     * already read an item.
     */
    public static final PermissionKey ITEMS_PRICE_CHECK = key("items.price.check");
    public static final PermissionKey MAIN_GROUP_SHOW = key("main.group.show");
    public static final PermissionKey MAIN_GROUP_CREATE = key("main.group.create");
    public static final PermissionKey MAIN_GROUP_UPDATE = key("main.group.update");
    public static final PermissionKey MAIN_GROUP_DELETE = key("main.group.delete");
    public static final PermissionKey SUB_GROUP_SHOW = key("sub.group.show");
    public static final PermissionKey SUB_GROUP_CREATE = key("sub.group.create");
    public static final PermissionKey SUB_GROUP_UPDATE = key("sub.group.update");
    public static final PermissionKey SUB_GROUP_DELETE = key("sub.group.delete");
    public static final PermissionKey INVENTORY_SHOW = key("inventory.show");
    public static final PermissionKey TREASURY_SHOW = key("treasury.show");
    public static final PermissionKey TREASURY_UPDATE = key("treasury.update");
    public static final PermissionKey TREASURY_DELETE = key("treasury.delete");
    // Separated on purpose: a cashier deposits and withdraws, and only the owner moves
    // money between treasuries or touches an opening balance. One "treasury.update" for
    // all four would have made the split impossible to express in a role.
    //
    // Both name their risk because both move cash and neither word is in risk(action)'s
    // table: TRANSFER and DEPOSIT would derive LOW.
    public static final PermissionKey TREASURY_TRANSFER = key("treasury.transfer", PermissionRisk.HIGH);
    public static final PermissionKey TREASURY_DEPOSIT = key("treasury.deposit", PermissionRisk.HIGH);
    // The owner's own money, and the number every balance is measured from. Both are
    // the owner's alone: a cashier who could record "capital paid in" could cover a
    // shortage with it, and one who could edit an opening balance could cover anything -
    // which is what CRITICAL means, and neither word derives it.
    public static final PermissionKey TREASURY_CAPITAL = key("treasury.capital", PermissionRisk.CRITICAL);
    public static final PermissionKey TREASURY_OPENING = key("treasury.opening", PermissionRisk.CRITICAL);
    /**
     * The currencies the shop deals in and their exchange rates (V80, docs/currency-plan.md). Three keys
     * because they are three trusts: reading a rate, recording today's, and deciding which currencies
     * exist and which one the books are in. Granted on upgrade to whoever may edit a treasury - the person
     * who decides what the tills are is the person who decides what they count in.
     */
    public static final PermissionKey CURRENCY_SHOW = key("currency.show");
    public static final PermissionKey CURRENCY_UPDATE = key("currency.update");
    public static final PermissionKey CURRENCY_RATE_UPDATE = key("currency.rate.update");
    public static final PermissionKey UNITS_SHOW = key("units.show");
    public static final PermissionKey UNITS_CREATE = key("units.create");
    public static final PermissionKey UNITS_UPDATE = key("units.update");
    public static final PermissionKey UNITS_DELETE = key("units.delete");
    /** The price tiers. There is no show or delete key: {@code SelPriceItemService} asks this one. */
    public static final PermissionKey SEL_PRICE_UPDATE = key("sel.price.update");
    public static final PermissionKey CUSTOMER_SHOW = key("customer.show");
    public static final PermissionKey CUSTOMER_CREATE = key("customer.create");
    public static final PermissionKey CUSTOMER_UPDATE = key("customer.update");
    public static final PermissionKey CUSTOMER_DELETE = key("customer.delete");
    public static final PermissionKey CUSTOMER_ACCOUNT_SHOW = key("customer.account.show");
    public static final PermissionKey CUSTOMER_ACCOUNT_CREATE = key("customer.account.create");
    public static final PermissionKey CUSTOMER_ACCOUNT_UPDATE = key("customer.account.update");
    public static final PermissionKey CUSTOMER_ACCOUNT_DELETE = key("customer.account.delete");
    /**
     * Recording a debit or credit note on a party's account - a movement with no cash behind
     * it. Separate from {@code account.create} on purpose, and granted by {@code V55} to
     * whoever already held it: collecting money is matched by cash in the drawer, while
     * adjusting a balance by decision is not, and the two are not the same trust. It is also
     * the only way to correct an opening balance once a party has moved, which
     * {@code OpeningBalanceGuard} forbids and {@code opening.correction.customers} promises.
     * <p>
     * The risk is named because {@code V55} inserts the row as {@code HIGH} and ADJUST derives
     * {@code LOW}, so the next startup's {@code synchronizeCatalog} overwrote the migration's answer
     * with the derived one. Two places said two things and the later one won.
     */
    public static final PermissionKey CUSTOMER_ACCOUNT_ADJUST = key("customer.account.adjust", PermissionRisk.HIGH);
    public static final PermissionKey SUPPLIERS_SHOW = key("suppliers.show");
    public static final PermissionKey SUPPLIERS_CREATE = key("suppliers.create");
    public static final PermissionKey SUPPLIERS_UPDATE = key("suppliers.update");
    public static final PermissionKey SUPPLIERS_DELETE = key("suppliers.delete");
    public static final PermissionKey SUPPLIERS_ACCOUNT_SHOW = key("suppliers.account.show");
    public static final PermissionKey SUPPLIERS_ACCOUNT_CREATE = key("suppliers.account.create");
    public static final PermissionKey SUPPLIERS_ACCOUNT_UPDATE = key("suppliers.account.update");
    public static final PermissionKey SUPPLIERS_ACCOUNT_DELETE = key("suppliers.account.delete");
    /** The supplier side of {@link #CUSTOMER_ACCOUNT_ADJUST}, risk included. */
    public static final PermissionKey SUPPLIERS_ACCOUNT_ADJUST = key("suppliers.account.adjust", PermissionRisk.HIGH);
    public static final PermissionKey EXPENSES_SHOW = key("expenses.show");
    public static final PermissionKey EXPENSES_CREATE = key("expenses.create");
    public static final PermissionKey EXPENSES_UPDATE = key("expenses.update");
    public static final PermissionKey EXPENSES_DELETE = key("expenses.delete");
    /**
     * Adding, renaming, moving and stopping the headings expenses are filed under (V64). There was no
     * screen for it before: the headings were six constants in {@code ExpensesType}. Granted on upgrade
     * to whoever could edit an expense.
     */
    public static final PermissionKey EXPENSES_HEADINGS_UPDATE = key("expenses.headings.update");
    /**
     * Printing and exporting the expenses list (V64). Apart from viewing it for the reason the ageing
     * report gives: a list on a screen is looked at, a file leaves the building. Granted on upgrade to
     * whoever may view the list, because the old screen's print button asked for nothing.
     */
    public static final PermissionKey EXPENSES_EXPORT = key("expenses.export");
    /**
     * The expense reports (V65): by heading, the year by month, the trend, by till, person, shift, payee
     * or month, and expenses against net sales. Apart from the list for the reason the reports exist -
     * the list says what was paid, the reports say where the business's money goes, which is a figure
     * about the business rather than a record of a drawer. Granted on upgrade to whoever may view the list.
     */
    public static final PermissionKey EXPENSES_REPORTS = key("expenses.reports");
    /**
     * Setting a budget for an expense heading (V66). Separate from the reports: reading where the money
     * went is not deciding what the shop is allowed to spend. Granted on upgrade to whoever manages the
     * headings, which is the nearest thing to it today.
     */
    public static final PermissionKey EXPENSES_BUDGET_MANAGE = key("expenses.budget.manage");
    /**
     * Managing the recurring-expense templates and their reminders (V66). A template records nothing by
     * itself - it reminds, and the person recording still needs {@code expenses.create} - but it decides
     * what the shop is reminded of. Granted on upgrade beside the budget key.
     */
    public static final PermissionKey EXPENSES_RECURRING_MANAGE = key("expenses.recurring.manage");
    public static final PermissionKey EMPLOYEE_SHOW = key("employee.show");
    public static final PermissionKey EMPLOYEE_CREATE = key("employee.create");
    public static final PermissionKey EMPLOYEE_UPDATE = key("employee.update");
    public static final PermissionKey EMPLOYEE_DELETE = key("employee.delete");
    /**
     * Recording what an employee is paid, from a given day.
     * <p>
     * Separate from {@link #EMPLOYEE_UPDATE} on purpose, and granted by {@code V57} to whoever
     * already held it: correcting a telephone number and deciding what somebody is paid every
     * month are not the same trust. It is also the only way to move the figure once the dated
     * history has begun - {@code SalaryChangeGuard} refuses the form after that, the way
     * {@code OpeningBalanceGuard} refuses an opening balance a party has moved past.
     */
    public static final PermissionKey EMPLOYEE_SALARY_CHANGE = key("employee.salary.change");
    /**
     * Reading one employee's account - what they have been paid, advanced and deducted.
     * <p>
     * V58 grants it to whoever already holds {@link #EMPLOYEES_SHOW_SALARY}, not to everyone who
     * can open the employees screen: a statement is a list of what somebody is paid, one line at
     * a time.
     */
    public static final PermissionKey EMPLOYEE_ACCOUNT_SHOW = key("employee.account.show");
    /**
     * Recording a deduction or an awarded bonus - a movement with no cash behind it.
     * <p>
     * Separate from {@link #EMPLOYEE_PAY} in both directions: a deduction takes no pound out of a
     * till, and the person who counts the drawer is not the person who decides an employee owes
     * two hundred. The same split V55 made between {@code account.create} and
     * {@code account.adjust} for the parties, for the same reason - cash is matched by a count,
     * a decision is matched by nothing. Same declared risk as those two, for that reason.
     */
    public static final PermissionKey EMPLOYEE_ACCOUNT_ADJUST = key("employee.account.adjust", PermissionRisk.HIGH);
    /**
     * Paying an employee out of a till.
     * <p>
     * It is the <em>additional</em> permission on top of {@link #EXPENSES_CREATE}, not a
     * replacement for it: every pound paid to an employee is an expense row (ق-١), so the payment
     * goes through {@code ExpenseService.recordForEmployee} and is refused without both. V58 grants this to
     * whoever holds {@code expenses.create}, so nobody loses an ability on upgrade.
     * <p>
     * PAY takes cash out of a drawer, so it names {@code HIGH}: the word is not in
     * {@link #risk(String)}'s table and would derive {@code LOW}, below the
     * {@code expenses.create} it is the addition to.
     */
    public static final PermissionKey EMPLOYEE_PAY = key("employee.pay", PermissionRisk.HIGH);

    public static final PermissionKey PAYROLL_SHOW = key("payroll.show");
    public static final PermissionKey PAYROLL_CREATE = key("payroll.create");

    /**
     * Approving a month's payroll.
     * <p>
     * Approval is what writes the entitlements into the employees' ledgers and freezes the lines,
     * so it is the moment the run stops being a working document. Separate from
     * {@link #PAYROLL_PAY} on purpose: <b>who computes does not disburse</b>. The cash is matched
     * by a count in the drawer; the decision that a month came to this figure is matched by
     * nothing, which is the same reasoning that separated {@code account.create} from
     * {@code account.adjust} in V55. Both words derive {@code LOW} and both are named instead.
     */
    public static final PermissionKey PAYROLL_APPROVE = key("payroll.approve", PermissionRisk.HIGH);
    public static final PermissionKey PAYROLL_PAY = key("payroll.pay", PermissionRisk.HIGH);

    /**
     * Seeing a delegate's commission rules: his target and his rates.
     * <p>
     * A rate is a figure about a person, as a salary is, so it is not fetched for a reader who
     * may not see one. V70 grants it to whoever already holds {@link #EMPLOYEES_SHOW_SALARY}.
     */
    public static final PermissionKey COMMISSION_SHOW = key("commission.show");

    /**
     * Setting the rule a delegate's commission is computed by.
     * <p>
     * Separate from {@link #EMPLOYEE_UPDATE} for the reason {@link #EMPLOYEE_SALARY_CHANGE} is:
     * correcting a telephone number is not deciding what somebody is paid. V70 grants it to
     * whoever holds the salary one, so nobody loses an ability on upgrade.
     */
    public static final PermissionKey COMMISSION_RULE_UPDATE = key("commission.rule.update");

    /**
     * The delegates' performance report: what each sold and collected in a month.
     * <p>
     * It does <b>not</b> open a target, a rate or a commission - those columns are fetched only
     * for a reader who also holds {@link #COMMISSION_SHOW}. V71 grants it to whoever holds that.
     */
    public static final PermissionKey COMMISSION_REPORTS = key("commission.reports");

    /**
     * Approving a month's commission, which freezes it. Seeing a run needs only
     * {@link #COMMISSION_SHOW}. V72 grants this and the two below to whoever holds
     * {@link #COMMISSION_RULE_UPDATE}.
     */
    public static final PermissionKey COMMISSION_RUN_CREATE = key("commission.run.create");

    /** Cancelling an approved run - possible only while none of it has been posted. */
    public static final PermissionKey COMMISSION_RUN_UPDATE = key("commission.run.update");

    /**
     * Posting an approved run to the delegates' accounts. {@code POST} derives
     * {@code CRITICAL}, which is its rank: it writes an entitlement into a person's account.
     */
    public static final PermissionKey COMMISSION_RUN_POST = key("commission.run.post");

    /**
     * Saving a sales invoice discounted beyond its delegate's ceiling
     * ({@code employees.max_discount_percent}, V73).
     * <p>
     * It guards no write of its own - the invoice's create or update permission does that - it
     * decides which of two answers {@code DelegateDiscountGuard} gives. V73 grants it to whoever
     * holds {@link #COMMISSION_RULE_UPDATE}, <b>not</b> to whoever may sell: granted to every
     * cashier, a ceiling the owner sets would stop nobody.
     */
    public static final PermissionKey SALES_DISCOUNT_OVERRIDE = key("sales.discount.override");

    public static final PermissionKey ATTENDANCE_SHOW = key("attendance.show");

    /**
     * Recording a day's attendance.
     * <p>
     * Separate from {@link #ATTENDANCE_SHOW} because marking the grid is a daily job for a
     * receptionist while reading it is a manager's, and separate from {@link #LEAVE_APPROVE}
     * because marking somebody absent is not the same as granting them leave.
     */
    public static final PermissionKey ATTENDANCE_RECORD = key("attendance.record");

    /** Asking for leave. It grants nothing, so it is the low-risk half of the pair. */
    public static final PermissionKey LEAVE_REQUEST = key("leave.request");

    /**
     * Deciding on leave - which writes the days onto the grid and so changes a month's pay, so it
     * names {@code HIGH} where APPROVE would derive {@code LOW}.
     */
    public static final PermissionKey LEAVE_APPROVE = key("leave.approve", PermissionRisk.HIGH);

    public static final PermissionKey JOB_SHOW = key("job.show");
    public static final PermissionKey JOB_CREATE = key("job.create");
    public static final PermissionKey JOB_UPDATE = key("job.update");
    public static final PermissionKey JOB_DELETE = key("job.delete");
    /**
     * Opening the settings screen, and every tab in it - and nothing else.
     * <p>
     * There were four more keys beside this one - {@code setting.company.show},
     * {@code setting.other.show}, {@code setting.items.show} and {@code setting.shows.show} - and
     * nothing read any of them: all four tabs, and the sidebar button, ask this key. They are gone
     * rather than wired, because which tabs a shop wants apart is a decision nobody has taken, and four
     * tick boxes that do nothing are worse than none. What the settings screen does grant separately
     * is what leaves the machine: {@link #SETTING_BACKUP_SHOW} and {@link #BACKUP_RESTORE}.
     * <p>
     * <b>It used to be the whole sidebar section called "settings" as well.</b> The section was
     * hidden without it, and with it the section's home, about, delete-data and close buttons were
     * all asking this one key - so a cashier given only their own shift screen could not reach it,
     * and one given this key to reach it could also wipe the database. The section now shows whenever
     * one of its buttons opens, and each button asks its own key: the delete-data button
     * {@link #SETTING_DATA_DELETE}, the shift screen {@link #SHIFT_SELF_VIEW}.
     */
    public static final PermissionKey SETTING_SHOW = key("setting.show");
    public static final PermissionKey COMPANY_UPDATE = key("company.update");
    /**
     * Opening the backup screen and taking a copy.
     * <p>
     * It was declared here and used by nothing: the sidebar button and the settings tab
     * both hung off {@link #SETTING_SHOW}, so anybody who could open the settings could
     * dump the whole database to a file and walk away with it. V39 grants it to every role
     * that already held {@code setting.show}, so nobody loses the ability they had.
     * <p>
     * It names {@code HIGH} rather than the {@code LOW} SHOW derives, for the reason in the sentence
     * above: what this opens is every customer, every invoice and every cost, in one file.
     */
    public static final PermissionKey SETTING_BACKUP_SHOW = key("setting.backup.show", PermissionRisk.HIGH);
    /**
     * Replacing the live database with a backup file.
     * <p>
     * Deliberately not the same key as taking one. A restore runs {@code DROP TABLE} over
     * the shop's data and is the single most destructive thing the program can do; a
     * backup is a read. V39 grants this one only to the roles that could already reach the
     * screen, and it can be taken away without taking backups away with it.
     */
    public static final PermissionKey BACKUP_RESTORE = key("backup.restore");
    /**
     * Emptying the program's tables - the "delete data" screen.
     * <p>
     * It hung off {@link #SETTING_SHOW}, so opening the settings was also the right to wipe the
     * database, and nothing in {@code WipeService} asked anything at all: the one lock was a password
     * written into the program. A cashier given the settings to change a printer could empty the
     * shop's books. The key is asked by the sidebar button and by {@code WipeService.run} itself.
     * <p>
     * No migration grants it: a new key reaches {@code SYSTEM_ADMIN} through the start-up
     * synchronisation and nobody else, on purpose - unlike {@code setting.backup.show}, which V39 gave
     * to every holder of {@code setting.show}, this is the one ability nobody should keep by accident.
     * DELETE derives {@code CRITICAL}.
     */
    public static final PermissionKey SETTING_DATA_DELETE = key("setting.data.delete");
    /**
     * Who may decide whether the home screen shows the day's totals - which is to say,
     * who may let the staff standing at that screen read the revenue.
     * <p>
     * The settings tab used to ask {@code CurrentUser.get().getId() != 1} instead: the
     * numbered-administrator test this permission system replaced, and one that breaks on
     * any install where the owner is not user number one. A newly declared key is granted
     * to SYSTEM_ADMIN by the startup synchronisation, so the person who could set it
     * before still can.
     */
    public static final PermissionKey MAIN_TOTALS_MANAGE = key("main.totals.manage");
    public static final PermissionKey INVOICE_PROFIT_SHOW = key("invoice.profit.show");
    public static final PermissionKey EMPLOYEES_SHOW_SALARY = key("employees.show.salary");
    public static final PermissionKey SHOW_COLUMN_BUY_PRICE = key("show.column.buy.price");
    /**
     * Editing a document dated before the current month, on the totals screen.
     * <p>
     * It predates {@code accounting_lock} (V9), which answers the same question properly - a closed
     * period, enforced in the service through {@code PeriodLock} - while this key is read in exactly
     * one place, {@code TotalsController.permissionButtons}, and so is silently off on any path that
     * does not go through that screen. Its read-only twin {@code show.data.before.month} was never
     * read anywhere and is gone. Whether this one should follow it is
     * {@code docs/permissions-plan.md} §4.5 - it changes what existing installs allow, so it is a
     * decision rather than a tidy-up.
     */
    public static final PermissionKey UPDATE_DATA_BEFORE_MONTH = key("update.data.before.month");
    public static final PermissionKey SETTING_UPDATE_NAME = key("setting.update.name");
    public static final PermissionKey SETTING_UPDATE_PASS = key("setting.update.pass");
    public static final PermissionKey REPORTS_SHOW_SUMMARY = key("reports.show.summary");
    public static final PermissionKey REPORTS_SHOW_ITEMS = key("reports.show.items");
    public static final PermissionKey REPORTS_SHOW_CUSTOMERS = key("reports.show.customers");
    public static final PermissionKey REPORTS_SHOW_SUPPLIERS = key("reports.show.suppliers");
    public static final PermissionKey REPORTS_SHOW_SALES = key("reports.show.sales");
    public static final PermissionKey REPORTS_SHOW_PURCHASE = key("reports.show.purchase");
    public static final PermissionKey REPORTS_SHOW_PROFIT = key("reports.show.profit");
    public static final PermissionKey REPORTS_SHOW_RETURNS = key("reports.show.returns");
    public static final PermissionKey STOCK_COUNT_SHOW = key("stock.count.show");
    /**
     * Entering a count sheet and keeping it as a draft (V74).
     * <p>
     * {@code save} asked {@link #STOCK_COUNT_SHOW}, so everyone who could open the screen could
     * write on it, and {@code deleteDraft} asked {@link #STOCK_COUNT_POST}, so discarding a sheet
     * that has moved nothing needed the right to move balances with one. A draft is not a count:
     * {@code adjustment_agg} reads only {@code POSTED}.
     * <p>
     * {@code HIGH} rather than the {@code CREATE} default, and for once that is the same answer:
     * what is being created is the sheet a posting is later made from.
     */
    public static final PermissionKey STOCK_COUNT_CREATE = key("stock.count.create");
    public static final PermissionKey STOCK_COUNT_POST = key("stock.count.post");
    /**
     * Reading the transfer history and its report (V74).
     * <p>
     * Both asked {@link #STOCK_TRANSFER_POST} until then: seeing what had been moved required the
     * right to move it. The same borrowing {@code V35} ended when the areas list stopped asking
     * for {@code items.show}.
     */
    public static final PermissionKey STOCK_TRANSFER_SHOW = key("stock.transfer.show");
    public static final PermissionKey STOCK_TRANSFER_POST = key("stock.transfer.post");
    public static final PermissionKey STOCK_TRANSFER_DELETE = key("stock.transfer.delete");
    public static final PermissionKey STOCK_SHOW = key("stock.show");
    public static final PermissionKey STOCK_CREATE = key("stock.create");
    public static final PermissionKey STOCK_UPDATE = key("stock.update");
    public static final PermissionKey STOCK_DELETE = key("stock.delete");
    public static final PermissionKey ACCOUNTING_LOCK_MANAGE = key("accounting.lock.manage");
    public static final PermissionKey ACCOUNTING_LOCK_BYPASS = key("accounting.lock.bypass");
    public static final PermissionKey USERS_SHOW = key("users.show");
    public static final PermissionKey USERS_MANAGE = key("users.manage");
    public static final PermissionKey ROLES_MANAGE = key("roles.manage");
    /**
     * Reading the areas list. An area belongs to a customer or a supplier, not to an item,
     * and until this key existed the section borrowed {@link #ITEMS_SHOW} - the permission
     * of the button that used to open it. V35 grants it to every role that held either.
     */
    public static final PermissionKey AREA_SHOW = key("area.show");
    public static final PermissionKey AREA_CREATE = key("area.create");
    public static final PermissionKey AREA_UPDATE = key("area.update");
    public static final PermissionKey AREA_DELETE = key("area.delete");
    // The four risks below were a chain of equals() inside definition(), which was the first sign
    // that deriving a risk from the last word of a key had reached its limit. They are declared
    // here instead, where the reason each is HIGH is the javadoc above it.
    /** Reading before/after database values; split from the broad settings permission in V46. */
    public static final PermissionKey AUDIT_VIEW = key("audit.view", PermissionRisk.HIGH);
    /** Writing sensitive before/after values outside the application. */
    public static final PermissionKey AUDIT_EXPORT = key("audit.export", PermissionRisk.HIGH);
    public static final PermissionKey AUDIT_DELETE = key("audit.delete");
    /** Enabling or executing permanent age-based deletion of audit rows. */
    public static final PermissionKey AUDIT_RETENTION_MANAGE = key("audit.retention.manage");
    /** Reading immutable evidence about export, deletion and retention administration. */
    public static final PermissionKey AUDIT_ADMIN_VIEW = key("audit.admin.view", PermissionRisk.HIGH);
    /** Exporting the immutable administration journal outside the application. */
    public static final PermissionKey AUDIT_ADMIN_EXPORT = key("audit.admin.export", PermissionRisk.HIGH);
    public static final PermissionKey USER_SHIFT_MANAGE = key("user.shift.manage");
    public static final PermissionKey SHIFT_SELF_VIEW = key("shift.self.view");
    public static final PermissionKey SHIFT_SELF_OPEN = key("shift.self.open");
    public static final PermissionKey SHIFT_SELF_CLOSE = key("shift.self.close");
    public static final PermissionKey SHIFT_X_REPORT_VIEW = key("shift.xreport.view");
    public static final PermissionKey SHIFT_FORCE_CLOSE = key("shift.force.close");
    public static final PermissionKey SHIFT_POLICY_MANAGE = key("shift.policy.manage");
    public static final PermissionKey SHIFT_REPORT_REPRINT = key("shift.report.reprint");
    public static final PermissionKey SHIFT_LEDGER_VIEW = key("shift.ledger.view");
    public static final PermissionKey PUBLIC_ACCESS = PermissionKey.publicAccess();
    public static final PermissionKey DISABLE_BUTTON = PermissionKey.deny();

    private static final List<PermissionDefinition> DEFINITIONS = discover();
    private static final Map<String, PermissionKey> BY_VALUE = DEFINITIONS.stream()
            .collect(Collectors.toUnmodifiableMap(definition -> definition.key().value(), PermissionDefinition::key));

    private AppPermissions() {
    }

    public static List<PermissionDefinition> definitions() {
        return DEFINITIONS;
    }

    public static PermissionKey fromValue(String value) {
        return BY_VALUE.get(value);
    }

    private static PermissionKey key(String value) {
        return PermissionKey.of(value);
    }

    /**
     * A key whose risk is stated rather than derived from its last word. Used where
     * {@link #risk(String)} is wrong about the key - never to repeat what it already gets right.
     */
    private static PermissionKey key(String value, PermissionRisk risk) {
        PermissionKey key = PermissionKey.of(value);
        DECLARED_RISKS.put(key.value(), Objects.requireNonNull(risk, "risk"));
        return key;
    }

    private static List<PermissionDefinition> discover() {
        return Arrays.stream(AppPermissions.class.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers())
                        && Modifier.isStatic(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())
                        && field.getType() == PermissionKey.class)
                .map(field -> {
                    try {
                        return (PermissionKey) field.get(null);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .filter(key -> !key.isMarker())
                .sorted(Comparator.comparing(PermissionKey::value))
                .map(key -> definition(key, 0))
                .toList();
    }

    /**
     * The module is the key's {@link PermissionGroup}, and used to be {@code parts[0]} uppercased -
     * which is what put {@code TOTAL}, {@code SHOW} and {@code SEL} in the roles screen's section
     * column and left 134 of 162 keys reading "general". A key belonging to no group is a build
     * failure rather than a silent {@code GENERAL}: see {@code PermissionCatalogArchitectureTest}.
     */
    private static PermissionDefinition definition(PermissionKey key, int sortOrder) {
        String[] parts = key.value().split("\\.");
        String module = PermissionGroup.of(key.value())
                .map(Enum::name)
                .orElseThrow(() -> new IllegalStateException(
                        "No PermissionGroup owns " + key.value() + " - add its prefix to one."));
        String action = parts[parts.length - 1].toUpperCase(Locale.ROOT);
        String resource = String.join(".", Arrays.copyOf(parts, parts.length - 1));
        PermissionRisk permissionRisk = DECLARED_RISKS.getOrDefault(key.value(), risk(action));
        return new PermissionDefinition(key, module, resource, action, permissionRisk, sortOrder);
    }

    /**
     * The risk of an action nothing declared one for. It reads the key's last word, so it answers
     * the four-verb families and falls through to {@code LOW} for everything else - which is why a
     * key whose last word is not here states its own risk at the declaration.
     */
    private static PermissionRisk risk(String action) {
        return switch (action) {
            // RESTORE replaces every table in the database from a file. There is no
            // action here that undoes more, so it cannot be the LOW the default gives it.
            case "DELETE", "BYPASS", "MANAGE", "POST", "RESTORE" -> PermissionRisk.CRITICAL;
            // OVERRIDE sets a rule aside for one document; it is not the LOW the default gives.
            case "UPDATE", "ADD", "CREATE", "MOVE", "OVERRIDE" -> PermissionRisk.HIGH;
            case "INVOICE", "PRICE", "SALARY", "PROFIT" -> PermissionRisk.MEDIUM;
            default -> PermissionRisk.LOW;
        };
    }
}
