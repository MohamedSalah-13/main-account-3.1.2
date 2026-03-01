package com.hamza.account.service;

import com.hamza.account.interfaces.api.AccountData;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.hamza.controlsfx.text.NumberUtils.roundToTwoDecimalPlaces;

public class AccountService {

    /**
     * @param <T3> for Names (Customers or Suppliers)
     * @param <T4> for Accounts (Customers or Suppliers)
     */
    public static <T3 extends BaseNames, T4 extends BaseAccount> List<T4> sumAccountForId(List<T4> list, AccountData<T4> accountData) {
        Map<Integer, List<T4>> map = groupBy(list, accountData.getNameIdFromAccount());
        for (Integer integer : map.keySet()) {
            List<T4> listOfAccount = map.get(integer);
            accountsListByGetAmount(listOfAccount);
        }

        return list.stream().sorted(Comparator.comparing(T4::getCreated_at)).toList();
    }

    /**
     * @param <T3> for Names (Customers or Suppliers)
     * @param <T4> for Accounts (Customers or Suppliers)
     */
    private static <T3 extends BaseNames, T4 extends BaseAccount> void accountsListByGetAmount(List<T4> list) {
        for (int i = 0; i < list.size(); i++) {
            T4 t4 = list.get(i);
            double purchase = t4.getPurchase();
            double paid = t4.getPaid();
            double v;
            if (i == 0) {
                v = purchase - paid;
            } else {
                T4 t4BeforeOne = list.get(i - 1);
                double amount = t4BeforeOne.getAmount();
                v = (amount + purchase) - paid;
            }
//            accountData.setAmount(t4, roundToTwoDecimalPlaces(v));
            t4.setAmount(roundToTwoDecimalPlaces(v));
        }
    }


    private static <E, K> Map<K, List<E>> groupBy(List<E> list, Function<E, K> keyFunction) {
        return Optional.ofNullable(list)
                .orElseGet(ArrayList::new)
                .stream()
                .collect(Collectors.groupingBy(keyFunction));
    }

}
