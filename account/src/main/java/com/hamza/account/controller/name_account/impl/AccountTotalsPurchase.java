package com.hamza.account.controller.name_account.impl;

import com.hamza.account.controller.model.AccountCard;
import com.hamza.account.controller.name_account.AccountDetailsInterface;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.service.PurchaseReService;
import com.hamza.account.service.PurchaseService;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.control.TreeItem;

import java.util.List;

/**
 * The supplier side: a document row is a purchase invoice or a purchase return, and its
 * lines come from {@code purchase} or {@code purchase_re}.
 * <p>
 * The two methods that assembled the statement are gone — see {@link AccountDetailsInterface}.
 */
public class AccountTotalsPurchase implements AccountDetailsInterface {

    private final PurchaseService purchaseService = ServiceRegistry.get(PurchaseService.class);
    private final PurchaseReService purchaseReService = ServiceRegistry.get(PurchaseReService.class);

    public static <T extends BasePurchasesAndSales> void addPurchaseItemsToTree(
            List<T> lines, TreeItem<AccountCard> treeItem) {
        String count = LanguageManager.getInstance().getString("party.statement.line.count");
        lines.forEach(line -> {
            var card = new AccountCard();
            card.setDetails(line.getTotal());
            card.setNotes(count + " ( " + formatQuantity(line.getQuantity()) + " ) "
                    + line.getItems().getNameItem() + " - " + line.getTotal());
            treeItem.getChildren().add(new TreeItem<>(card));
        });
    }

    private static String formatQuantity(double quantity) {
        if (quantity == Math.floor(quantity)) {
            return String.valueOf((int) quantity);
        }
        return String.valueOf(quantity);
    }

    @Override
    public void addTreeItemTotals(AccountCard row, TreeItem<AccountCard> treeItem) throws Exception {
        switch (row.getKind()) {
            case INVOICE -> addPurchaseItemsToTree(purchaseService.fetchByInvoiceNumber(row.getId()), treeItem);
            case RETURN -> addPurchaseItemsToTree(purchaseReService.fetchByInvoiceNumber(row.getId()), treeItem);
            default -> {
                // Nothing underneath an opening balance or a payment.
            }
        }
    }
}
