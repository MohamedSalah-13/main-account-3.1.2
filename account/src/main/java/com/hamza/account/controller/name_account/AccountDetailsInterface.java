package com.hamza.account.controller.name_account;

import com.hamza.account.controller.model.AccountCard;
import javafx.scene.control.TreeItem;

/**
 * The one thing a statement still needs per party side: the lines of a document.
 * <p>
 * It used to also assemble the statement itself — {@code getTotalList} and
 * {@code getTotalReturnList} built the invoice and return rows from the document services,
 * and the controller added the opening balance and the payments around them. That was a
 * second definition of what a party owes, competing with {@code account_customer_table},
 * and it carried the {@code invoice_type == CASH} branch that {@code V15} removed from the
 * views: a deferred return contributed nothing, so a customer who returned goods on
 * account still appeared to owe them. Both methods are gone, and
 * {@link com.hamza.account.features.party.statement.PartyStatementService} is where a
 * statement comes from now.
 * <p>
 * What is left is genuinely per side and genuinely a different query: which line table to
 * read for an expanded row.
 */
public interface AccountDetailsInterface {

    /**
     * Loads the lines of the document a row stands for, as children of its tree item.
     * <p>
     * Called only for a row whose {@link AccountCard#hasDocumentLines()} is true, and only
     * when the user expands it — the statement of a party with two thousand invoices must
     * not read two thousand line tables to open.
     */
    void addTreeItemTotals(AccountCard row, TreeItem<AccountCard> treeItem) throws Exception;
}
