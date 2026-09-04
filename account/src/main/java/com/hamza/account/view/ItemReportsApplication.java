package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.items.ItemReportsController;
import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * Opens the item reports in a window of their own.
 * <p>
 * It takes the filter the items screen was showing, so a report opened from a narrowed list
 * answers about those rows rather than silently widening to the whole catalogue - which is
 * the difference between a report and a surprise.
 */
public class ItemReportsApplication extends Application {

    private final ItemReportsController controller = new ItemReportsController();

    public ItemReportsApplication(ItemCatalogFilter filter) {
        controller.setOpeningFilter(filter);
    }

    @Override
    public void start(Stage stage) throws Exception {
        // SceneAll, not a plain Scene: it applies the theme and the RTL orientation, and a
        // window that skipped it would open unstyled and left-to-right.
        stage.setScene(new SceneAll(new OpenFxmlApplication(controller).getPane()));
        stage.setTitle(LanguageManager.getInstance().getString("itemreport.screen.title"));
        stage.getIcons().add(new Image(new Image_Setting().itemWhite));
        stage.setWidth(1200);
        stage.setHeight(760);
        stage.show();
    }
}
