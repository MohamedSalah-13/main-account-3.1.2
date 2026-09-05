package com.hamza.account.controller.items;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.service.ItemsService;
import com.hamza.account.view.ItemImageApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.control.TableView;

/**
 * Compatibility entry point for the item-search dialog.
 * <p>
 * The old implementation decoded a picture in every table cell. This now adds only the
 * lightweight Show action and lets the separate window load one picture on demand.
 */
public record ColumnImage(TableView<ItemsModel> tableView, ItemsService itemsService) {

    public void addColumnImage() {
        ItemImageActionColumn.addTo(tableView, this::showImage);
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
