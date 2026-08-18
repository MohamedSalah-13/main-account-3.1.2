package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import com.hamza.account.type.ProcessType;
import com.hamza.controlsfx.table.ColumnData;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class CardItems extends DForColumnTable {

    private int id;
    @ColumnData(titleName = NamesTables.CODE_INVOICE)
    private int invoice_num;

    private int numItem;
    //    @ColumnData(titleName = NamesTables.NAME_ITEM)
    private String nameItem;
    @ColumnData(titleName = NamesTables.DATE)
    private LocalDate invoice_date;
    @ColumnData(titleName = NamesTables.NAME)
    private String name_account;
    @ColumnData(titleName = NamesTables.TYPE)
    private String type_name;
    @ColumnData(titleName = NamesTables.QUANTITY)
    private double quantity;
    /**
     * The factor the line stored ({@code type_value}), not what the units screen says
     * today: an item's factor may change, and a past carton has to keep meaning what
     * it meant when it was sold.
     */
    private double typeValue;
    /**
     * {@link #quantity} in the item's base unit, signed the way the movement goes -
     * positive for a purchase or a sales return, negative for a sale or a purchase
     * return. Computed by {@code card_item_view_details}, so the card and
     * {@code quantity_items_table} count a line the same way.
     */
    private double baseQuantity;
    @ColumnData(titleName = NamesTables.PRICE)
    private double price;
    private double buyPrice;
    private double profit;
    @ColumnData(titleName = NamesTables.DISCOUNT)
    private double discount;
    @ColumnData(titleName = NamesTables.TOTAL)
    private double totals;
    /** The item's running balance in base units after this movement. */
    @ColumnData(titleName = NamesTables.BALANCE)
    private double balance;
    private ProcessType processType;

    /** The raw {@code sales} / {@code sales_re} / ... the queries filter on. */
    private String table_name;
    /** {@link #table_name} in the user's language - what the column shows. */
    @ColumnData(titleName = NamesTables.PROCESS_TYPE)
    private String processTypeName;

    private int typeCode;
    private String barcode;
    private int delegate_id;
    @ColumnData(titleName = NamesTables.DELEGATE)
    private String delegate_name;

    private LocalDate endDate;

    public CardItems(int numItem, String nameItem, double quantity) {
        this.numItem = numItem;
        this.nameItem = nameItem;
        this.quantity = quantity;
    }
}
