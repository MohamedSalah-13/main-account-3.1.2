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
            .build();

    public static final DeleteRule ITEMS = DeleteRule.forEntity("delete.entity.item")
            .requirePermission(AppPermissions.ITEMS_DELETE)
            .referencedBy("sales", "num", "delete.ref.sales_line")
            .referencedBy("sales_re", "item_id", "delete.ref.sales_return_line")
            .referencedBy("purchase", "num", "delete.ref.purchase_line")
            .referencedBy("purchase_re", "item_id", "delete.ref.purchase_return_line")
            .referencedBy("stock_movements", "item_id", "delete.ref.stock_movement")
            .referencedBy("stock_transfer_list", "item_id", "delete.ref.stock_transfer_line")
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
            .referencedBy("treasury_deposit_expenses", "treasury_id", "delete.ref.deposit_or_withdrawal")
            .referencedBy("treasury_transfers", "treasury_from", "delete.ref.transfer_out")
            .referencedBy("treasury_transfers", "treasury_to", "delete.ref.transfer_in")
            .build();

    public static final DeleteRule MAIN_GROUPS = DeleteRule.forEntity("delete.entity.main_group")
            .requirePermission(AppPermissions.MAIN_GROUP_DELETE)
            .protectId(1, "delete.protect.main_group.default")
            .referencedBy("sub_group", "main_id", "delete.ref.sub_group")
            .build();

    public static final DeleteRule SUB_GROUPS = DeleteRule.forEntity("delete.entity.sub_group")
            .requirePermission(AppPermissions.SUB_GROUP_DELETE)
            .protectId(1, "delete.protect.sub_group.default")
            .referencedBy("items", "sub_num", "delete.ref.item")
            .build();

    /** Employee 1 is the seeded "بيع مباشر" delegate. */
    public static final DeleteRule EMPLOYEES = DeleteRule.forEntity("delete.entity.employee")
            .requirePermission(AppPermissions.EMPLOYEE_DELETE)
            .protectId(1, "delete.protect.employee.direct_sale")
            .referencedBy("total_sales", "delegate_id", "delete.ref.sales_invoice")
            .referencedBy("total_sales_re", "delegate_id", "delete.ref.sales_return")
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
