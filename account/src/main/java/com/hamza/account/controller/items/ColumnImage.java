package com.hamza.account.controller.items;

import com.hamza.account.config.AppIcon;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.service.ItemsService;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.view.ItemImageApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.control.TableView;

import java.util.List;

/**
 * Compatibility entry point for the item-search dialog.
 * <p>
 * The old implementation decoded a picture in every table cell. This adds only one icon button
 * per row - through {@link RowActionsColumn}, the one row-button cell, rather than a hand-rolled
 * one of its own - and lets the separate window load one picture on demand.
 */
public record ColumnImage(TableView<ItemsModel> tableView, ItemsService itemsService) {

    public void addColumnImage() {
        tableView.getColumns().add(RowActionsColumn.of("item.image", List.of(
                RowAction.of("item.image", AppIcon.SHOW, "app-neutral-button", null, this::showImage))));
    }

    private void showImage(ItemsModel item) {
        try {
            ItemImageApplication.show(tableView.getScene() == null ? null : tableView.getScene().getWindow(),
                    item.getId(), item.getNameItem(), itemsService);
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("item.image"), e);
        }
    }
}
