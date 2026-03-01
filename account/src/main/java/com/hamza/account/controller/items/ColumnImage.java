package com.hamza.account.controller.items;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.service.ItemsService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import lombok.extern.log4j.Log4j2;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;

import static com.hamza.account.config.PropertiesName.getItemImageHint;

@Log4j2
public record ColumnImage(TableView<ItemsModel> tableView, ItemsService itemsService) {
    public void addColumnImage() {
        TableColumn<ItemsModel, String> imageColumn = new TableColumn<>("Image");
        imageColumn.setCellFactory(column -> new TableCell<>() {
            private final ImageView imageView = new ImageView();
            private final Popup popup = new Popup();
            private final ImageView popupImageView = new ImageView();
            private final ContextMenu contextMenu = new ContextMenu();

            // Introduce constants to avoid repetition and magic values
            private final FileChooser.ExtensionFilter IMAGE_FILES_FILTER =
                    new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.gif");
            private final String MSG_IMAGE_UPDATED = "Item image updated successfully";

            {
                imageView.setFitHeight(100);
                imageView.setFitWidth(100);
                imageView.setPreserveRatio(true);

                popupImageView.setFitHeight(400);
                popupImageView.setFitWidth(400);
                popupImageView.setPreserveRatio(true);

                popup.getContent().add(popupImageView);
                popup.setAutoHide(true);

                var downloadImage = "حفظ الصورة";
                var editImage = "تعديل الصورة";
                var deleteImage = "حذف الصورة";
                var refresh = "تحديث";

                MenuItem downloadItem = new MenuItem(downloadImage);
                MenuItem editItem = new MenuItem(editImage);
                MenuItem deleteItem = new MenuItem(deleteImage);
                MenuItem refreshItem = new MenuItem(refresh);

                // Extract method + Guard clauses + Consolidated error handling
                downloadItem.setOnAction(e -> {
                    ItemsModel item = currentItem();
                    if (!hasImage(item)) return;

                    FileChooser chooser = newImageFileChooser("Save Image");
                    File file = chooser.showSaveDialog(getScene().getWindow());
                    if (file == null) return;

                    try {
                        Files.write(file.toPath(), item.getItem_image());
                    } catch (IOException ex) {
                        handleException("Error saving image", ex);
                    }
                });

                // Extract method + Guard clauses + Consolidated image update flow
                editItem.setOnAction(e -> {
                    ItemsModel item = currentItem();
                    if (item == null) return;

                    FileChooser chooser = newImageFileChooser("Choose Image");
                    File file = chooser.showOpenDialog(getScene().getWindow());
                    if (file == null) return;

                    try {
                        byte[] imageData = Files.readAllBytes(file.toPath());
                        persistImageChange(item, imageData, MSG_IMAGE_UPDATED);
                    } catch (IOException | DaoException ex) {
                        handleException("Error updating image", ex);
                    }
                });

                // Extract method + Guard clauses
                deleteItem.setOnAction(e -> {
                    ItemsModel item = currentItem();
                    if (!hasImage(item)) return;
                    if (!AllAlerts.confirmDelete()) return;

                    try {
                        persistImageChange(item, new byte[0], MSG_IMAGE_UPDATED);
                    } catch (DaoException ex) {
                        handleException("Error deleting image", ex);
                    }
                });

                refreshItem.setOnAction(e -> updateItem(null, false));

                contextMenu.getItems().addAll(downloadItem, editItem, deleteItem, refreshItem);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
//                    setContextMenu(null);
                } else {
                    ItemsModel itemsModel = getTableRow().getItem();
                    if (itemsModel.getItem_image() != null && itemsModel.getItem_image().length > 0) {
                        Image image = new Image(new ByteArrayInputStream(itemsModel.getItem_image()));
                        imageView.setImage(image);
                        popupImageView.setImage(image);
                        setGraphic(imageView);
                        setContextMenu(contextMenu);
                        setOnMouseEntered(event -> {
                            if (getItemImageHint())
                                popup.show(this, event.getScreenX() + 15, event.getScreenY() + 15);
                        });
                        setOnMouseExited(event -> {
                            if (getItemImageHint()) popup.hide();
                        });

                    } else {
                        setGraphic(null);
                        setContextMenu(contextMenu);
                    }
                }
            }

            // --- Extracted helpers (reduce duplication, improve readability) ---
            private ItemsModel currentItem() {
                return getTableRow() != null ? getTableRow().getItem() : null;
            }

            private boolean hasImage(ItemsModel item) {
                return item != null && item.getItem_image() != null;
            }

            private FileChooser newImageFileChooser(String title) {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle(title);
                fileChooser.getExtensionFilters().setAll(IMAGE_FILES_FILTER);
                return fileChooser;
            }

            private void persistImageChange(ItemsModel item, byte[] imageData, String successMessage) throws DaoException {
                item.setItem_image(imageData);
                item.setItemsUnitsModelList(new ArrayList<>());

                var i = itemsService.commitItemUpdate(item);
                if (i > 1) {
                    AllAlerts.alertSaveWithMessage(successMessage);
                    updateItem(null, false);
                }
            }

            private void handleException(String userMessage, Exception ex) {
                log.error(ex.getMessage(), ex);
                AllAlerts.alertError(userMessage + ": " + ex.getMessage());
            }
        });
        imageColumn.setMinWidth(60);
        tableView.getColumns().addLast(imageColumn);
    }

}
