package com.hamza.account.controller.name_account.impl;

import com.hamza.account.controller.model.AccountCard;
import com.hamza.account.controller.name_account.AccountDetailsInterface;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.type.InvoiceType;
import javafx.scene.control.TreeItem;

import java.util.List;

public class AccountTotalsPurchase extends ServiceData implements AccountDetailsInterface {

    public static final String purchaseLabel = "المشتريات";
    public static final String purchaseReturnLabel = "مرتجع المشتريات";
    public static final String count = "عدد";

    public AccountTotalsPurchase(DaoFactory daoFactory) throws Exception {
        super(daoFactory);
    }

    public static <T extends BasePurchasesAndSales> void addPurchaseItemsToTree(List<T> purchaseList, TreeItem<AccountCard> accountTreeItem) {
        purchaseList.forEach(purchase -> {
            var accountCard = new AccountCard();
            accountCard.setDetails(purchase.getTotal());
            accountCard.setNotes(count + " ( " + formatQuantity(purchase.getQuantity()) + " ) " + purchase.getItems().getNameItem()
                    + " - " + purchase.getTotal());
            accountTreeItem.getChildren().add(new TreeItem<>(accountCard));
        });
    }

    private static String formatQuantity(double quantity) {
        if (quantity == Math.floor(quantity)) {
            return String.valueOf((int) quantity);
        } else {
            return String.valueOf(quantity);
        }
    }

    @Override
    public void getTotalList(List<AccountCard> list_items, int num_id) throws Exception {
        var list = totalBuyService.getTotalBuyBySupId(num_id);
        list.forEach(totalSales -> {
            AccountCard accountCard = new AccountCard(totalSales.getId(), purchaseLabel, totalSales.getDate(), totalSales.getTotal_after_discount(), totalSales.getPaid()
                    , 0, totalSales.getNotes(), purchaseLabel);
            list_items.add(accountCard);
        });

    }

    @Override
    public void getTotalReturnList(List<AccountCard> list_items, int num_id) throws Exception {
        var list = totalBuyReturnService.getTotalBuyBySupId(num_id);
        list.forEach(total_buy_re -> {
            double total = 0;
            if (total_buy_re.getInvoiceType().equals(InvoiceType.CASH)) total = total_buy_re.getTotal_after_discount();
            AccountCard accountCard = new AccountCard(total_buy_re.getId(), purchaseReturnLabel, total_buy_re.getDate(), total, total_buy_re.getPaid()
                    , 0, total_buy_re.getNotes(), purchaseReturnLabel);
            list_items.add(accountCard);
        });
    }

    @Override
    public void addTreeItemTotals(AccountCard t4, TreeItem<AccountCard> accountTreeItem) throws Exception {
        var id = t4.getId();

        if (t4.getInformation().equals(purchaseLabel)) {
            var purchaseList = purchaseService.fetchByInvoiceNumber(id);
            addPurchaseItemsToTree(purchaseList, accountTreeItem);
        }

        if (t4.getInformation().equals(purchaseReturnLabel)) {
            var purchaseList = purchaseReService.fetchByInvoiceNumber(id);
            addPurchaseItemsToTree(purchaseList, accountTreeItem);
        }
    }
}
