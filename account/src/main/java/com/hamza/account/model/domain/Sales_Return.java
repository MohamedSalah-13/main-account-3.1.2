package com.hamza.account.model.domain;

import com.hamza.account.model.base.BasePurchasesAndSales;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class Sales_Return extends BasePurchasesAndSales {

    private boolean item_has_package;
}


