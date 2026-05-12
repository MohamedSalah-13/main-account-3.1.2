package com.hamza.account.view;

import com.hamza.account.config.ThemeManager;
import com.hamza.controlsfx.interfaceData.TableViewShowDataInt;
import com.hamza.controlsfx.interfaceData.ToolbarAccountInt;
import com.hamza.controlsfx.others.ChangeOrientation;
import com.hamza.controlsfx.view.ApplicationDataWithToolbarIndexApp;
import javafx.scene.Node;
import org.jetbrains.annotations.NotNull;

public class OpenApplicationWithData<T> {


    public OpenApplicationWithData(ToolbarAccountInt<T> toolbarAccountInt, @NotNull TableViewShowDataInt<T> tableViewShowDataInt
            , Node node, String title) throws Exception {
        ApplicationDataWithToolbarIndexApp<T> withToolbarIndexApp = new ApplicationDataWithToolbarIndexApp<>(toolbarAccountInt, tableViewShowDataInt, node, title);
        var dialogPane = withToolbarIndexApp.getDialogPane();
        dialogPane.getStylesheets().add(ThemeManager.getStylesheet());
        ThemeManager.apply(dialogPane.getScene());
        ChangeOrientation.sceneOrientation(dialogPane.getScene());
        withToolbarIndexApp.show();
    }
}
