package com.hamza.account.view;

import com.hamza.account.controller.invoice.ShowInvoiceController;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import lombok.extern.log4j.Log4j2;

/**
 * @param <T1> for purchase or sales or purchase return or sales return
 * @param <T2> for Totals (purchase or sales or purchase return or sales return)
 * @param <T3> for Names (Customers or Suppliers)
 * @param <T4> for Accounts (Customers or Suppliers)
 */
@Log4j2
public class ShowInvoiceApplication<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount> {

    public ShowInvoiceApplication(DataPublisher dataPublisher, DataInterface<T1, T2, T3, T4> dataInterface
            , DaoFactory daoFactory, int num, String name) throws Exception {
        new OpenApplication<>(new ShowInvoiceController<>(dataInterface, daoFactory, dataPublisher, num, name));
    }

}
