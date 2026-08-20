package com.hamza.account.model.domain;

import com.hamza.account.model.base.BasePurchasesAndSales;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

@Setter
@Getter
@NoArgsConstructor
public class Purchase extends BasePurchasesAndSales implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Suppliers suppliers;

}


