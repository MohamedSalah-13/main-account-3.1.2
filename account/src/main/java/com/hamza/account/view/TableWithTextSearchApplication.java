package com.hamza.account.view;

import com.hamza.account.controller.others.TableWithTextSearchController;
import com.hamza.account.controller.pos.DialogButtons;
import com.hamza.account.controller.search.SearchInterface;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.Pane;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;

@Getter
@Log4j2
public class TableWithTextSearchApplication<T> extends Dialog<T> {

    private static final String WINDOW_WIDTH = "WindowWidth";
    private static final String WINDOW_HEIGHT = "WindowHeight";
    private static final String WINDOW_X = "WindowX";
    private static final String WINDOW_Y = "WindowY";
    private final TableWithTextSearchController<T> searchItems;
    private final java.util.prefs.Preferences preferences = java.util.prefs.Preferences.userNodeForPackage(TableWithTextSearchApplication.class);

    public TableWithTextSearchApplication(SearchInterface<T> searchInterface) throws IOException {
        searchItems = new TableWithTextSearchController<>(searchInterface);
        DialogPane dialogPane = this.getDialogPane();
        dialogPane.setHeaderText(Setting_Language.WORD_SEARCH);
        setTitle(Setting_Language.WORD_SEARCH);
        setResizable(true);
        ButtonType ok = ButtonType.OK;
        ButtonType cancel = ButtonType.CANCEL;
        dialogPane.getButtonTypes().addAll(ok, cancel);

        Pane pane = new OpenFxmlApplication(searchItems).getPane();
        dialogPane.setContent(pane);

        this.setResultConverter((var1x) -> {
            ButtonBar.ButtonData var2 = var1x == null ? null : var1x.getButtonData();
            return var2 == ButtonBar.ButtonData.OK_DONE ? searchItems.selectedItemProperty().get() : null;
        });

        DialogButtons.changeNameAndGraphic(dialogPane);

        restoreWindowBounds();
        setOnCloseRequest(event -> saveWindowBounds());

    }

    private void saveWindowBounds() {
        preferences.putDouble(WINDOW_WIDTH, getWidth());
        preferences.putDouble(WINDOW_HEIGHT, getHeight());
        preferences.putDouble(WINDOW_X, getX());
        preferences.putDouble(WINDOW_Y, getY());
    }

    private void restoreWindowBounds() {
        setWidth(preferences.getDouble(WINDOW_WIDTH, 800));
        setHeight(preferences.getDouble(WINDOW_HEIGHT, 600));
        setX(preferences.getDouble(WINDOW_X, -1));
        setY(preferences.getDouble(WINDOW_Y, -1));
    }
}
