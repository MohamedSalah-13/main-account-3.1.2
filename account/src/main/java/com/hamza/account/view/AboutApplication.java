package com.hamza.account.view;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.PropertiesName;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.productprofile.ProductFeatureCatalog;
import com.hamza.account.features.productprofile.ProductProfile;
import com.hamza.account.service.version.SystemInfoDialog;
import com.hamza.account.trial.TrialManager;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Properties;

@Log4j2
public class AboutApplication extends Application {
    private static final String GAFATA = "Gafata";
    private static final String GRAND_HOTEL = "Grand Hotel";
    private static final String NEW_ROCKER = "New Rocker";
    private final VBox box;
    private Text statusText;
    private Text remainingText;
    private Text licenseText;

    public AboutApplication() {
        LanguageManager language = LanguageManager.getInstance();
        Button button = new Button(language.getString("about.system.info"));
        button.getStyleClass().add("app-neutral-button");
        button.setOnAction(event -> new SystemInfoDialog().show());
        button.setGraphic(AppIcon.INFO.graphic());

        box = new VBox(20);
        box.getChildren().addAll(AppIcon.INFO.graphic(80), getLabel(),
                buildProductProfileCard(), buildLicenseActions(), button);
        box.setPadding(new Insets(30));
        box.setAlignment(Pos.TOP_CENTER);
    }

    public static void main(String[] args) {
        launch(args);
    }

    private static String getBuildDate() {
        try (var is = MainScreenApplication.class.getResourceAsStream("/version.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String pv = props.getProperty("build.date");
                if (pv != null && !pv.isBlank() && !pv.contains("${")) {
                    return pv;
                }
            }
        } catch (Exception ignored) {
        }

        return LanguageManager.getInstance().getString("about.build.development");
    }

    @Override
    public void start(Stage stage) throws Exception {
        Scene scene = new SceneAll(box);
        stage.setScene(scene);
        stage.setTitle(LanguageManager.getInstance().getString("nav.about"));
        stage.setResizable(false);
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.show();

        ChangeOrientation.sceneOrientation(scene);
        ThemeManager.apply(scene);
    }

    private TextFlow getLabel() {
        TextFlow flow = new TextFlow();
        flow.setTextAlignment(TextAlignment.CENTER);
        flow.setLineSpacing(5);
        double size = 20;
        String color = "green";

        LanguageManager language = LanguageManager.getInstance();
        extracted(language.getString("about.version", PropertiesName.getAppLastRunVersion()) + "\n",
                GRAND_HOTEL, size, color, flow);
        extracted(language.getString("about.build.date", getBuildDate()) + "\n", GAFATA, size, color, flow);

        statusText = createText("", GAFATA, size, color);
        remainingText = createText("", GAFATA, size, color);
        licenseText = createText("", GAFATA, size, color);
        flow.getChildren().addAll(statusText, remainingText, licenseText);

        extracted(language.getString("about.powered.by") + "\n", GAFATA, size, color, flow);
        extracted(language.getString("about.copyright") + "\n", GAFATA, size, color, flow);
        extracted(com.hamza.controlsfx.language.Setting_Language.PROGRAM_NAME_EN + "\n",
                GAFATA, size + 5, "red", flow);
        extracted(com.hamza.controlsfx.language.Setting_Language.PROGRAM_TEL,
                GAFATA, size + 5, "red", flow);

        refreshStatus();
        return flow;
    }

    private void extracted(String text, String fontName, double fontSize, String color, TextFlow flow) {
        final Text t1 = new Text(text);
        t1.setStyle("-fx-font-family: '" + fontName + "'; -fx-font-size: " + fontSize + "; -fx-fill: " + color + " ");
        flow.getChildren().add(t1);
    }

    private Text createText(String text, String fontName, double fontSize, String color) {
        final Text t1 = new Text(text);
        t1.setStyle("-fx-font-family: '" + fontName + "'; -fx-font-size: " + fontSize + "; -fx-fill: " + color + " ");
        return t1;
    }

    private void applyStyle(Text text, String fontName, double fontSize, String color) {
        text.setStyle("-fx-font-family: '" + fontName + "'; -fx-font-size: " + fontSize + "; -fx-fill: " + color + " ");
    }

    private HBox buildLicenseActions() {
        LanguageManager language = LanguageManager.getInstance();
        Button activate = new Button(language.getString("about.license.activate"));
        activate.getStyleClass().add("app-neutral-button");
        activate.setGraphic(AppIcon.SECURITY.graphic());
        activate.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(language.getString("about.license.choose"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    language.getString("about.license.file.type"), "*.dat"));
            var file = chooser.showOpenDialog(activate.getScene().getWindow());
            if (file == null) {
                return;
            }
            try {
                Files.copy(file.toPath(), TrialManager.getLicensePath(), StandardCopyOption.REPLACE_EXISTING);
                AllAlerts.alertSaveWithMessage(language.getString("about.license.activated"));
                refreshStatus();
            } catch (Exception e) {
                AllAlerts.handleError(language.getString("about.license.activate"), e);
            }
        });

        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER);
        box.getChildren().addAll(activate);
        return box;
    }

    private VBox buildProductProfileCard() {
        LanguageManager language = LanguageManager.getInstance();
        VBox card = new VBox(6);
        card.getStyleClass().add("app-card");
        card.setMaxWidth(520);
        Label heading = new Label(language.getString("product.profile.about.heading"));
        heading.getStyleClass().add("app-section-title");
        card.getChildren().add(heading);

        ProductProfile profile = ServiceRegistry.get(ProductProfile.class);
        if (profile == null) {
            card.getChildren().add(new Label(language.getString("product.profile.about.unavailable")));
            return card;
        }
        String customer = profile.legacyFallback()
                ? language.getString("product.profile.about.legacy.customer") : profile.customerName();
        String edition = profile.legacyFallback()
                ? language.getString("product.profile.about.legacy.edition") : profile.profileName();
        card.getChildren().addAll(
                new Label(language.getString("product.profile.about.customer", customer)),
                new Label(language.getString("product.profile.about.edition", edition)),
                new Label(language.getString("product.profile.about.features",
                        profile.enabledFeatures().size(), ProductFeatureCatalog.standard().keys().size())));
        if (!profile.legacyFallback()) {
            DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                    .withLocale(language.getCurrentLocale())
                    .withZone(ZoneId.systemDefault());
            card.getChildren().add(new Label(language.getString(
                    "product.profile.about.issued", formatter.format(profile.issuedAt()))));
        }
        return card;
    }

    /**
     * Reads the trial state against a pooled connection and returns that
     * connection as soon as the read is done. Keeping a TrialManager - and with it
     * an open connection - for the lifetime of this window leaked one pooled
     * connection every time the window was opened, and opened a second
     * ConnectionToDatabase, re-reading and decrypting config.xml, to get it.
     */
    private TrialManager.TrialDisplayInfo loadDisplayInfo() {
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            return new TrialManager(connection).getDisplayInfo();
        } catch (Exception e) {
            log.error("Error reading the trial status", e);
            return null;
        } finally {
            ConnectionManager.release(connection);
        }
    }

    private void refreshStatus() {
        LanguageManager language = LanguageManager.getInstance();
        double size = 20;
        String ok = "green";
        String warn = "orange";
        String bad = "red";

        TrialManager.TrialDisplayInfo info = loadDisplayInfo();
        if (info == null) {
            statusText.setText(language.getString("about.status.unavailable") + "\n");
            remainingText.setText(language.getString("about.remaining.unavailable") + "\n");
            licenseText.setText(language.getString("about.license.unavailable") + "\n");
            applyStyle(statusText, GAFATA, size, bad);
            applyStyle(remainingText, GAFATA, size, bad);
            applyStyle(licenseText, GAFATA, size, bad);
            return;
        }

        if (info.licenseValid) {
            statusText.setText(language.getString("about.status.activated") + "\n");
            remainingText.setText(language.getString("about.remaining.unlimited") + "\n");
            licenseText.setText(language.getString("about.license.valid") + "\n");
            applyStyle(statusText, GAFATA, size, ok);
            applyStyle(remainingText, GAFATA, size, ok);
            applyStyle(licenseText, GAFATA, size, ok);
            return;
        }

        statusText.setText(language.getString("about.status.trial") + "\n");
        if (info.daysRemaining != null) {
            long days = Math.max(0, info.daysRemaining);
            remainingText.setText(language.getString("about.remaining.days", days) + "\n");
        } else {
            remainingText.setText(language.getString("about.remaining.unavailable") + "\n");
        }

        if (info.licensePresent) {
            licenseText.setText(language.getString("about.license.invalid") + "\n");
        } else {
            licenseText.setText(language.getString("about.license.missing") + "\n");
        }

        String statusColor = info.trialExpired ? bad : warn;
        applyStyle(statusText, GAFATA, size, statusColor);
        applyStyle(remainingText, GAFATA, size, statusColor);
        applyStyle(licenseText, GAFATA, size, statusColor);
    }
}
