package com.hamza.account.view;

import com.hamza.account.Main;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.users.SupportRecoverySignerPane;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

/**
 * Standalone technician utility for everything signed with the release private key: authoring
 * and applying client editions, and answering a customer's administrator-recovery request.
 * Neither tab keeps the key; each asks for the file when it signs.
 */
public final class ProductProfileSetupApplication extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        LanguageManager language = LanguageManager.getInstance();
        FXMLLoader loader = new FXMLLoader(
                Main.class.getResource("view/product-profile-setup.fxml"), language.getResourceBundle());
        Parent profile = loader.load();

        Tab profileTab = new Tab(language.getString("product.profile.setup.tab"), profile);
        Tab recoveryTab = new Tab(language.getString("support.recovery.signer.tab"),
                new SupportRecoverySignerPane().root());
        TabPane tabs = new TabPane(profileTab, recoveryTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Scene scene = new Scene(tabs, 940, 760);
        ThemeManager.apply(scene);
        ChangeOrientation.sceneOrientation(scene);
        stage.setTitle(language.getString("product.profile.setup.title"));
        stage.setMinWidth(820);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();
    }
}
