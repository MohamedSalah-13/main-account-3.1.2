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
}