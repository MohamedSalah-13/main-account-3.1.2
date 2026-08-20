package com.hamza.account.model.domain;

import com.hamza.account.model.base.BaseNames;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class Customers extends BaseNames {

    private double credit_limit;
    private SelPriceTypeModel selPriceObject;

    public Customers(int id) {
        setId(id);
    }

    public Customers(int id, String name) {
        setId(id);
        setName(name);
    }

    public Customers(String name, double limit, double firstBalance) {
        setName(name);
        setFirst_balance(firstBalance);
        this.credit_limit = limit;
    }

}
