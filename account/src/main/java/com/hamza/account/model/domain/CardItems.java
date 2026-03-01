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
    @ColumnData(titleName = NamesTables.PRICE)
    private double price;
    private double buyPrice;
    private double profit;
    @ColumnData(titleName = NamesTables.DISCOUNT)
    private double discount;
    @ColumnData(titleName = NamesTables.TOTAL)
    private double totals;
    private ProcessType processType;

    @ColumnData(titleName = "نوع العملية")
    private String table_name;

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
