package com.hamza.account.features.export;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CustomerAccountData {
    private String customerName;
    private double debit;
    private double credit;
    private double balance;
}

