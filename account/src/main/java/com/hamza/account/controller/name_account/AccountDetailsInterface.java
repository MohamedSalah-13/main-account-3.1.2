package com.hamza.account.controller.name_account;

import javafx.scene.control.TreeItem;

import java.util.List;

public interface AccountDetailsInterface {

    void getTotalList(List<AccountCard> list_items, int num_id) throws Exception;

    void getTotalReturnList(List<AccountCard> list_items, int num_id) throws Exception;

    void addTreeItemTotals(AccountCard t4, TreeItem<AccountCard> accountTreeItem) throws Exception;
}
