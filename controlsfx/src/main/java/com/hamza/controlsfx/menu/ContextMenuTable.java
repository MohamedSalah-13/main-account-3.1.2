package com.hamza.controlsfx.menu;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

public class ContextMenuTable extends ContextMenu {

    private final MenuItem addData = new MenuItem("جديد");
    private final MenuItem updateData = new MenuItem("تعديل");
    private final MenuItem deleteData = new MenuItem("حذف");
    private final MenuItem refreshData = new MenuItem("تحديث");

    public ContextMenuTable(ActionTable actionTable) {
        addData.setGraphic(icon_all(FontAwesomeIcon.FILE_TEXT));
        updateData.setGraphic(icon_all(FontAwesomeIcon.EDIT));
        deleteData.setGraphic(icon_all(FontAwesomeIcon.CLOSE));
        refreshData.setGraphic(icon_all(FontAwesomeIcon.REFRESH));

//        addData.setAccelerator(KeyCodeCombinationSetting.KEY_ADD);
//        updateData.setAccelerator(KeyCodeCombinationSetting.KEY_UPDATE);
//        deleteData.setAccelerator(KeyCodeCombinationSetting.KEY_DELETE);
        getItems().addAll(addData, updateData, deleteData, new SeparatorMenuItem(), refreshData);
        actionAll(actionTable);
    }

    private void actionAll(ActionTable actionTable) {
        addData.setOnAction(event -> actionTable.actionAdd());
        updateData.setOnAction(event -> actionTable.actionUpdate());
        deleteData.setOnAction(event -> actionTable.actionDelete());
        refreshData.setOnAction(event -> actionTable.actionRefresh());
    }

    private FontAwesomeIconView icon_all(FontAwesomeIcon icon) {
        FontAwesomeIconView iconView = new FontAwesomeIconView(icon);
        String DEFAULT_STYLE_CLASS_ICONS = "icons";
        iconView.getStyleClass().add(DEFAULT_STYLE_CLASS_ICONS);
        return iconView;
    }
}