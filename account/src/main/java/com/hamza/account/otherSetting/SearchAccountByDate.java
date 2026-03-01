package com.hamza.account.otherSetting;

import com.hamza.account.interfaces.api.AccountData;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.controlsfx.dateTime.SearchByDate;

import java.util.List;

/**
 * Represents a search operation for accounts filtered by date.
 *
 * @param list        the list of account objects to be searched
 * @param firstDate   the starting date for the search filter
 * @param lastDate    the ending date for the search filter
 * @param accountData the account data interface providing account-related operations
 * @param <T3>        the type representing names (customers or suppliers)
 * @param <T4>        the type representing accounts (customers or suppliers)
 */
public record SearchAccountByDate<T3 extends BaseNames, T4 extends BaseAccount>(List<T4> list, String firstDate,
                                                                                String lastDate,
                                                                                AccountData<T4> accountData) implements SearchByDate<T4> {

    @Override
    public String getDate(T4 t4) {
        return t4.getDate();
    }
}
