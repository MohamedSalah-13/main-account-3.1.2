package com.hamza.account.interfaces.spinner;

import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;

import java.util.function.Function;

/**
 * @param <T3> for Names (Customers or Suppliers)
 * @param <T4> for Accounts (Customers or Suppliers)
 */
public interface SpinnerInterface<T3 extends BaseNames, T4 extends BaseAccount> {

    Function<T4, Double> getAmount();

    T4 objectAccount(T3 t3, double amount);

    T3 objectName(T4 t4);

    int nameId(T3 t3);

    String nameString(T3 t3);
}
