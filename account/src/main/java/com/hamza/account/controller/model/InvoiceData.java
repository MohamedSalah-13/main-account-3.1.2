package com.hamza.account.controller.model;

import com.hamza.account.type.InvoiceType;
import lombok.Data;

@Data
public class InvoiceData {

    private InvoiceType invoiceType;
    private double paid;
    private boolean printCustomer;
    private boolean printToKitchen;
    private boolean printInvoice;
}
