package com.hamza.account.delete;

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
    public static final DeleteRule UNITS = DeleteRule.forEntity("الوحدة")
            .requirePermission(AppPermissions.UNITS_DELETE)
            .protectId(1, "لا يمكن حذف الوحدة الافتراضية")
            .referencedBy("items", "unit_id", "صنف")
            .referencedBy("items_units", "unit", "وحدة صنف")
            .referencedBy("sales", "type", "سطر فاتورة بيع")
            .referencedBy("sales_re", "type", "سطر مرتجع بيع")
            .referencedBy("purchase", "type", "سطر فاتورة شراء")
            .referencedBy("purchase_re", "type", "سطر مرتجع شراء")
            .referencedBy("stock_movements", "unit_id", "حركة مخزون")
            .build();

    public static final DeleteRule ITEMS = DeleteRule.forEntity("الصنف")
            .requirePermission(AppPermissions.ITEMS_DELETE)
            .referencedBy("sales", "num", "سطر فاتورة بيع")
            .referencedBy("sales_re", "item_id", "سطر مرتجع بيع")
            .referencedBy("purchase", "num", "سطر فاتورة شراء")
            .referencedBy("purchase_re", "item_id", "سطر مرتجع شراء")
            .referencedBy("stock_movements", "item_id", "حركة مخزون")
            .referencedBy("stock_transfer_list", "item_id", "سطر تحويل مخزني")
            .build();

    /** Customer 1 is "بيع نقدى", which the sales screen falls back to. */
    public static final DeleteRule CUSTOMERS = DeleteRule.forEntity("العميل")
            .requirePermission(AppPermissions.CUSTOMER_DELETE)
            .protectId(1, "لا يمكن حذف عميل البيع النقدى")
            .referencedBy("total_sales", "sup_code", "فاتورة بيع")
            .referencedBy("total_sales_re", "sup_id", "مرتجع بيع")
            .referencedBy("customers_accounts", "account_code", "حركة حساب")
            .build();

    /** Supplier 1 is the seeded "مورد عام". */
    public static final DeleteRule SUPPLIERS = DeleteRule.forEntity("المورد")
            .requirePermission(AppPermissions.SUPPLIERS_DELETE)
            .protectId(1, "لا يمكن حذف المورد العام")
            .referencedBy("total_buy", "sup_code", "فاتورة شراء")
            .referencedBy("total_buy_re", "sup_id", "مرتجع شراء")
            .referencedBy("suppliers_accounts", "account_code", "حركة حساب")
            .build();

    /** Treasury 1 is the seeded "الخزينة الرئيسية" and the DEFAULT behind every treasury_id. */
    public static final DeleteRule TREASURIES = DeleteRule.forEntity("الخزينة")
            .requirePermission(AppPermissions.TREASURY_DELETE)
            .protectId(1, "لا يمكن حذف الخزينة الرئيسية")
            .referencedBy("total_sales", "treasury_id", "فاتورة بيع")
            .referencedBy("total_sales_re", "treasury_id", "مرتجع بيع")
            .referencedBy("total_buy", "treasury_id", "فاتورة شراء")
            .referencedBy("total_buy_re", "treasury_id", "مرتجع شراء")
            .referencedBy("customers_accounts", "treasury_id", "حركة حساب عميل")
            .referencedBy("suppliers_accounts", "treasury_id", "حركة حساب مورد")
            .referencedBy("expenses_details", "treasury_id", "مصروف")
            .referencedBy("treasury_deposit_expenses", "treasury_id", "إيداع أو صرف")
            .referencedBy("treasury_transfers", "treasury_from", "تحويل صادر")
            .referencedBy("treasury_transfers", "treasury_to", "تحويل وارد")
            .build();

    public static final DeleteRule MAIN_GROUPS = DeleteRule.forEntity("المجموعة الرئيسية")
            .requirePermission(AppPermissions.MAIN_GROUP_DELETE)
            .protectId(1, "لا يمكن حذف المجموعة الرئيسية الافتراضية")
            .referencedBy("sub_group", "main_id", "مجموعة فرعية")
            .build();

    public static final DeleteRule SUB_GROUPS = DeleteRule.forEntity("المجموعة الفرعية")
            .requirePermission(AppPermissions.SUB_GROUP_DELETE)
            .protectId(1, "لا يمكن حذف المجموعة الفرعية الافتراضية")
            .referencedBy("items", "sub_num", "صنف")
            .build();

    /** Employee 1 is the seeded "بيع مباشر" delegate. */
    public static final DeleteRule EMPLOYEES = DeleteRule.forEntity("الموظف")
            .requirePermission(AppPermissions.EMPLOYEE_DELETE)
            .protectId(1, "لا يمكن حذف موظف البيع المباشر")
            .referencedBy("total_sales", "delegate_id", "فاتورة بيع")
            .referencedBy("total_sales_re", "delegate_id", "مرتجع بيع")
            .referencedBy("expense_salary", "employee_id", "مرتب")
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
    public static final DeleteRule EXPENSES_DETAILS = DeleteRule.forEntity("المصروف")
            .requirePermission(AppPermissions.EXPENSES_DELETE)
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
