package com.hamza.account.view;

import com.hamza.account.controller.others.SearchItemsController;
import com.hamza.account.features.invoice.ItemPickRequest;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Modal item-picker window with an explicit result instead of a property side channel. */
public final class SearchItemsApplication {

    private final SearchItemsController controller;

    public SearchItemsApplication(DataInterface<?, ?, ?, ?> dataInterface, int priceTier) {
        controller = new SearchItemsController(dataInterface, priceTier);
    }

    public Optional<List<ItemPickRequest>> showAndWait(Window owner) throws IOException {
        Stage stage = new Stage();
        Scene scene = new SceneAll(new OpenFxmlApplication(controller).getPane());
        stage.setScene(scene);
        stage.setTitle(LanguageManager.getInstance().getString("search.items.title"));
        stage.setResizable(true);
        stage.setMinWidth(820);
        stage.setMinHeight(600);
        if (owner == null) {
            stage.initModality(Modality.APPLICATION_MODAL);
        } else {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        stage.showAndWait();
        return controller.result();
    }
}
