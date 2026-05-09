package com.hamza.account.interfaces;

import com.hamza.account.model.domain.CustomerPurchasedItem;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public interface CustomerPurchaseInterface {

    List<CustomerPurchasedItem> getPurchasedItemsByCustomerId(int customerId) throws DaoException;

    String title();
}
