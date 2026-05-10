package com.hamza.account.model.domain;

import lombok.Data;

@Data
public class CustomerReceivable {
    private int customerId;
    private String customerName;
    private String customerPhone;
    private double invoicesDebt;     // ديون الفواتير
    private double openingBalance;   // مديونية سابقة
    private double totalReceivable;  // الإجمالي المستحق
}