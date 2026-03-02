package com.hamza.account.controller.name_account.impl;

import com.hamza.account.controller.name_account.AccountCard;
import com.hamza.account.controller.name_account.AccountDetailsInterface;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.type.InvoiceType;
import javafx.scene.control.TreeItem;

import java.util.List;

import static com.hamza.account.controller.name_account.impl.AccountTotalsPurchase.addPurchaseItemsToTree;

public class AccountTotalsSales extends ServiceData implements AccountDetailsInterface {

    public static final String salesTitle = "المبيعات";
    public static final String salesReTitle = "مرتجع المبيعات";

    public AccountTotalsSales(DaoFactory daoFactory) throws Exception {
        super(daoFactory);
    }

    @Override
    public void getTotalList(List<AccountCard> list_items, int num_id) throws Exception {
        var list = totalSalesService.getTotalSalesByCustomerId(num_id);
        list.forEach(totalSales -> {
            AccountCard accountCard = new AccountCard(totalSales.getId(), salesTitle, totalSales.getDate(), totalSales.getTotal_after_discount(), totalSales.getPaid()
                    , 0, totalSales.getNotes(), salesTitle);
            list_items.add(accountCard);
        });

    }

    @Override
    public void getTotalReturnList(List<AccountCard> list_items, int num_id) throws Exception {
        var list = totalSalesReturnService.getTotalSalesByCustomerId(num_id);
        list.forEach(totalSalesRe -> {
            double total = 0;
            if (totalSalesRe.getInvoiceType().equals(InvoiceType.CASH)) total = totalSalesRe.getTotal_after_discount();

            AccountCard accountCard = new AccountCard(totalSalesRe.getId(), salesReTitle, totalSalesRe.getDate(), total, totalSalesRe.getPaid()
                    , 0, totalSalesRe.getNotes(), salesReTitle);
            list_items.add(accountCard);
        });
    }

    @Override
    public void addTreeItemTotals(AccountCard t4, TreeItem<AccountCard> accountTreeItem) throws Exception {
        var id = t4.getId();

        if (t4.getInformation().equals(salesTitle)) {
            var purchaseList = salesService.fetchByInvoiceNumber(id);
            addPurchaseItemsToTree(purchaseList, accountTreeItem);
        }

        if (t4.getInformation().equals(salesReTitle)) {
            var purchaseList = salesReService.fetchByInvoiceNumber(id);
            addPurchaseItemsToTree(purchaseList, accountTreeItem);
        }
    }
}
