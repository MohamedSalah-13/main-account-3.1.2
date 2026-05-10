package com.hamza.account.model.domain;

import com.hamza.controlsfx.table.ColumnData;
import lombok.Data;

@Data
public class CustomerReceivable {
    private int customerId;
    @ColumnData(titleName = "Customer Name")
    private String customerName;
    @ColumnData(titleName = "Customer Phone")
    private String customerPhone;
    @ColumnData(titleName = "Invoices")
    private double invoicesDebt;
    @ColumnData(titleName = "Opening Balance")
    private double openingBalance;
    @ColumnData(titleName = "Total Payments")
    private double totalPayments;
    @ColumnData(titleName = "Total Receivable")
    private double totalReceivable;
}