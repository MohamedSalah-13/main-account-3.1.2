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

    /**
     * The delegate this customer's invoices default to, or 0 for none (V56).
     * <p>
     * On the customer only: {@code total_sales.delegate_id} has existed since V1 and is
     * picked afresh on every invoice with nothing remembering it, while a purchase has
     * no delegate at all. There is deliberately no foreign key - the delegates are rows
     * in {@code users}, and a key here would refuse to retire a user for as long as one
     * customer still named them.
     */
    private int default_delegate_id;

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
