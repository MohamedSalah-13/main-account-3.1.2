package com.hamza.account.controller.items;

import com.hamza.account.config.AppIcon;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.function.Consumer;

/**
 * Adds lightweight row actions to an items table.
 * <p>
 * The column deliberately contains no image value. The Show button receives only the row
 * and leaves loading the picture to the separate viewer, so JavaFX never decodes a picture
 * while it is laying out or scrolling the catalogue. The item-search dialog uses the
 * image-only overload; the catalogue also supplies its edit and delete commands.
 */
public final class ItemImageActionColumn {

    private ItemImageActionColumn() {
    }

    public static void addTo(TableView<ItemsModel> tableView, Consumer<ItemsModel> showImage) {
        addTo(tableView, showImage, null, null, false, false);
    }

    public static void addTo(TableView<ItemsModel> tableView,
                             Consumer<ItemsModel> showImage,
                             Consumer<ItemsModel> editItem,
                             Consumer<ItemsModel> deleteItem,
                             boolean canEdit,
                             boolean canDelete) {
        boolean hasRowCommands = editItem != null && deleteItem != null;
        TableColumn<ItemsModel, ItemsModel> column = new TableColumn<>();
        column.setId(hasRowCommands ? "items_row_actions" : "items_image_action");
        column.setGraphic((hasRowCommands ? AppIcon.SETTINGS : AppIcon.SHOW).graphic(16));
        column.setSortable(false);
        column.setReorderable(false);
        column.setResizable(false);
        double width = hasRowCommands ? 270 : 92;
        column.setMinWidth(width);
        column.setPrefWidth(width);
        column.setMaxWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(ignored -> new RowActionsCell(
                showImage, editItem, deleteItem, canEdit, canDelete));
        tableView.getColumns().add(column);
    }

    private static final class RowActionsCell extends TableCell<ItemsModel, ItemsModel> {

        private final HBox actions = new HBox(6);

        private RowActionsCell(Consumer<ItemsModel> showImage,
                               Consumer<ItemsModel> editItem,
                               Consumer<ItemsModel> deleteItem,
                               boolean canEdit,
                               boolean canDelete) {
            actions.setAlignment(Pos.CENTER);
            actions.getChildren().add(actionButton(
                    "show", AppIcon.SHOW, "neutral-button", showImage, false));
            if (editItem != null) {
                actions.getChildren().add(actionButton(
                        "edit", AppIcon.EDIT, "warning-button", editItem, !canEdit));
            }
            if (deleteItem != null) {
                actions.getChildren().add(actionButton(
                        "delete", AppIcon.DELETE, "danger-button", deleteItem, !canDelete));
            }
            setAlignment(Pos.CENTER);
        }

        private Button actionButton(String textKey,
                                    AppIcon icon,
                                    String styleClass,
                                    Consumer<ItemsModel> command,
                                    boolean disabled) {
            Button button = new Button(
                    LanguageManager.getInstance().getString(textKey), icon.graphic(14));
            button.getStyleClass().add(styleClass);
            button.setDisable(disabled);
            button.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(button, Priority.ALWAYS);
            button.setOnAction(event -> {
                ItemsModel item = getItem();
                if (item != null) {
                    getTableView().getSelectionModel().select(getIndex());
                    command.accept(item);
                }
            });
            return button;
        }

        @Override
        protected void updateItem(ItemsModel value, boolean empty) {
            super.updateItem(value, empty);
            setGraphic(empty || value == null ? null : actions);
        }
    }
}
