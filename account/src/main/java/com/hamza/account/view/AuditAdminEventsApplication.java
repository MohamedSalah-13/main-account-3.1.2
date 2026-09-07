package com.hamza.account.view;

import com.hamza.account.controller.others.AuditAdminEventsController;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Opens the read-only audit administration history as an owned modal window. */
public final class AuditAdminEventsApplication {

    private AuditAdminEventsApplication() {
    }

    public static void show(Window owner) throws Exception {
        Stage stage = new Stage();
        Scene scene = new SceneAll(new OpenFxmlApplication(new AuditAdminEventsController()).getPane());
        stage.setScene(scene);
        stage.setTitle(LanguageManager.getInstance().getString("audit.admin.title"));
        stage.setMinWidth(1080);
        stage.setMinHeight(700);
        stage.setResizable(true);
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
            if (owner instanceof Stage ownerStage) {
                stage.getIcons().setAll(ownerStage.getIcons());
            }
        }
        stage.show();
    }
}
