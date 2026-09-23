package com.hamza.account.model.domain;

import lombok.Data;

@Data
public class CustomerReceivable {
    private int customerId;
    private String customerName;
    private String customerPhone;
    private double invoicesDebt;
    private double openingBalance;
    private double totalPayments;
    private double totalReceivable;
    /**
     * What the customer owes in their own currency (V82, docs/currency-plan.md §14) - the figure their
     * credit limit, written in that currency, is held against. The base figure for a customer in the base.
     */
    private double totalReceivableOwn;
}