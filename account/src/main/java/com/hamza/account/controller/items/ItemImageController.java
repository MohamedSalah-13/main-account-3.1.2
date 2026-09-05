package com.hamza.account.controller.items;

import com.hamza.account.config.AppIcon;
import com.hamza.account.features.items.ItemImageContent;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.ItemsService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.ByteArrayInputStream;

/** JavaFX binding for the separate, lazy-loaded item-picture window. */
@FxmlPath(pathFile = "items/item-image-view.fxml")
public final class ItemImageController {

    private final int itemId;
    private final String itemName;
    private final ItemsService itemsService;

    @FXML private StackPane imagePane;
    @FXML private ImageView imageView;
    @FXML private VBox stateBox;
    @FXML private ProgressIndicator progress;
    @FXML private Label labelItemName, labelStatus;
    @FXML private Button btnClose;

    private Task<ItemImageContent> loadTask;

    public ItemImageController(int itemId, String itemName, ItemsService itemsService) {
        this.itemId = itemId;
        this.itemName = itemName == null ? "" : itemName;
        this.itemsService = itemsService;
    }

    public void initialize() {
        labelItemName.setText(itemName);
        btnClose.setGraphic(AppIcon.CLOSE.graphic(16));
        btnClose.setOnAction(event -> btnClose.getScene().getWindow().hide());
        imageView.fitWidthProperty().bind(imagePane.widthProperty().subtract(32));
        imageView.fitHeightProperty().bind(imagePane.heightProperty().subtract(32));
        loadImage();
    }

    private void loadImage() {
        showStatus(LanguageManager.getInstance().getString("item.image.loading"), true);
        loadTask = new Task<>() {
            @Override
            protected ItemImageContent call() throws Exception {
                return itemsService.getItemImage(itemId);
            }
        };
        loadTask.setOnSucceeded(event -> showImage(loadTask.getValue()));
        loadTask.setOnFailed(event -> {
            showStatus(LanguageManager.getInstance().getString("item.image.invalid"), false);
            AllAlerts.handleError(LanguageManager.getInstance().getString("item.image"),
                    new Exception(loadTask.getException()));
        });
        Thread thread = new Thread(loadTask, "item-image-loader-" + itemId);
        thread.setDaemon(true);
        thread.start();
    }

    private void showImage(ItemImageContent content) {
        if (content == null || !content.isAvailable()) {
            showStatus(LanguageManager.getInstance().getString("item.image.empty"), false);
            return;
        }
        Image image = new Image(new ByteArrayInputStream(content.bytes()));
        if (image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0) {
            showStatus(LanguageManager.getInstance().getString("item.image.invalid"), false);
            return;
        }
        imageView.setImage(image);
        imageView.setVisible(true);
        imageView.setManaged(true);
        stateBox.setVisible(false);
        stateBox.setManaged(false);
    }

    private void showStatus(String text, boolean busy) {
        imageView.setImage(null);
        imageView.setVisible(false);
        imageView.setManaged(false);
        stateBox.setVisible(true);
        stateBox.setManaged(true);
        progress.setVisible(busy);
        progress.setManaged(busy);
        labelStatus.setText(text);
    }

    public void dispose() {
        if (loadTask != null) loadTask.cancel();
        imageView.setImage(null);
    }
}
