package com.hamza.account.controller.name_account.impl;

import com.hamza.account.controller.model.AccountCard;
import com.hamza.account.controller.name_account.AccountDetailsInterface;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.service.SalesReService;
import com.hamza.account.service.SalesService;
import javafx.scene.control.TreeItem;

import static com.hamza.account.controller.name_account.impl.AccountTotalsPurchase.addPurchaseItemsToTree;

/**
 * The customer side: a statement row that is a document is a sales invoice or a sales
 * return, and its lines come from {@code sales} or {@code sales_re}.
 * <p>
 * The two methods that assembled the statement itself are gone — see
 * {@link AccountDetailsInterface}. With them went the two Arabic label constants this class
 * published, which the statement screen compared its rows against to decide what to colour
 * and what to expand.
 */
public class AccountTotalsSales implements AccountDetailsInterface {

    private final SalesService salesService = ServiceRegistry.get(SalesService.class);
    private final SalesReService salesReService = ServiceRegistry.get(SalesReService.class);

    @Override
    public void addTreeItemTotals(AccountCard row, TreeItem<AccountCard> treeItem) throws Exception {
        // The kind, not a translated label. An invoice row's id is its invoice number.
        switch (row.getKind()) {
            case INVOICE -> addPurchaseItemsToTree(salesService.fetchByInvoiceNumber(row.getId()), treeItem);
            case RETURN -> addPurchaseItemsToTree(salesReService.fetchByInvoiceNumber(row.getId()), treeItem);
            default -> {
                // An opening balance and a payment have no lines; the caller does not ask.
            }
        }
    }
}
