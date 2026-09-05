package com.hamza.account.view;

import com.hamza.account.controller.items.ItemImageController;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.service.ItemsService;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;

/** Opens one item's picture in a separate, themed window. */
public final class ItemImageApplication {

    private ItemImageApplication() {
    }

    public static void show(Window owner, int itemId, String itemName, ItemsService itemsService)
            throws IOException {
        ItemImageController controller = new ItemImageController(itemId, itemName, itemsService);
        Scene scene = new SceneAll(new OpenFxmlApplication(controller).getPane());

        Stage stage = new Stage();
        if (owner != null) stage.initOwner(owner);
        stage.initModality(owner == null ? Modality.APPLICATION_MODAL : Modality.WINDOW_MODAL);
        stage.setTitle(LanguageManager.getInstance().getString("item.image"));
        stage.setScene(scene);
        stage.setWidth(760);
        stage.setHeight(600);
        stage.setMinWidth(520);
        stage.setMinHeight(420);
        stage.setOnHidden(event -> controller.dispose());
        stage.show();
    }
}
