package com.hamza.account.view;

import com.hamza.account.config.ConnectionToDatabase;
import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.PropertiesName;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.service.version.SystemInfoDialog;
import com.hamza.account.trial.TrialManager;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.ChangeOrientation;
import com.hamza.controlsfx.util.ImageChoose;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
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
import java.util.Properties;

@Log4j2
public class AboutApplication extends Application {
    private static final String GAFATA = "Gafata";
    private static final String GRAND_HOTEL = "Grand Hotel";
    private static final String NEW_ROCKER = "New Rocker";
    private static final String BUILD_DATE = "${buildDate}";
    private final VBox box;
    private final TrialManager trialManager;
    private Text statusText;
    private Text remainingText;
    private Text licenseText;

    public AboutApplication() {
        var imageSetting = new Image_Setting();
        ImageView imageView = new ImageDesign(imageSetting.tools, 120);
        Button button = new Button(Setting_Language.WORD_SHOW);
        button.getStyleClass().add("app-neutral-button");
        button.setOnAction(event -> new SystemInfoDialog().show());
        button.setGraphic(ImageChoose.createIcon(imageSetting.cancel));

        trialManager = createTrialManager();

        box = new VBox(20);
        box.getChildren().addAll(imageView, getLabel(), buildLicenseActions(), button);
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

        return "dev";
    }

    @Override
    public void start(Stage stage) throws Exception {
        Scene scene = new SceneAll(box);
        stage.setScene(scene);
        stage.setTitle(Setting_Language.ABOUT);
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().tools));
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

        extracted("version_" + PropertiesName.getAppLastRunVersion() + "\n", GRAND_HOTEL, size, color, flow);
        extracted("Build on " + getBuildDate() + "\n", GAFATA, size, color, flow);

        statusText = createText("", GAFATA, size, color);
        remainingText = createText("", GAFATA, size, color);
        licenseText = createText("", GAFATA, size, color);
        flow.getChildren().addAll(statusText, remainingText, licenseText);

        extracted("Power by Hamza Software" + "\n", GAFATA, size, color, flow);
        extracted("Copyright(c) 2020-2025" + "\n", GAFATA, size, color, flow);
        extracted(Setting_Language.PROGRAM_NAME_EN + "\n", GAFATA, size + 5, "red", flow);
        extracted(Setting_Language.PROGRAM_TEL, GAFATA, size + 5, "red", flow);

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
        Button activate = new Button("تفعيل الترخيص");
        activate.getStyleClass().add("app-neutral-button");
        activate.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("اختر ملف الترخيص");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("License File (*.dat)", "*.dat"));
            var file = chooser.showOpenDialog(activate.getScene().getWindow());
            if (file == null) {
                return;
            }
            try {
                Files.copy(file.toPath(), TrialManager.getLicensePath(), StandardCopyOption.REPLACE_EXISTING);
                AllAlerts.alertSaveWithMessage("تم تفعيل الترخيص بنجاح.");
                refreshStatus();
            } catch (Exception e) {
                log.error("Error activating license", e);
                AllAlerts.alertError("فشل تفعيل الترخيص.");
            }
        });

        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER);
        box.getChildren().addAll(activate);
        return box;
    }

    private TrialManager createTrialManager() {
        try {
            var connection = new ConnectionToDatabase().getDbConnection().getConnection();
            return new TrialManager(connection);
        } catch (Exception e) {
            log.error("Error creating TrialManager", e);
            return null;
        }
    }

    private void refreshStatus() {
        double size = 20;
        String ok = "green";
        String warn = "orange";
        String bad = "red";

        if (trialManager == null) {
            statusText.setText("الحالة: غير متاح\n");
            remainingText.setText("الوقت المتبقي: غير متاح\n");
            licenseText.setText("الترخيص: غير متاح\n");
            applyStyle(statusText, GAFATA, size, bad);
            applyStyle(remainingText, GAFATA, size, bad);
            applyStyle(licenseText, GAFATA, size, bad);
            return;
        }

        TrialManager.TrialDisplayInfo info = trialManager.getDisplayInfo();
        if (info.licenseValid) {
            statusText.setText("الحالة: مفعلة\n");
            remainingText.setText("الوقت المتبقي: غير محدود\n");
            licenseText.setText("الترخيص: صالح\n");
            applyStyle(statusText, GAFATA, size, ok);
            applyStyle(remainingText, GAFATA, size, ok);
            applyStyle(licenseText, GAFATA, size, ok);
            return;
        }

        statusText.setText("الحالة: تجريبية\n");
        if (info.daysRemaining != null) {
            long days = Math.max(0, info.daysRemaining);
            remainingText.setText("الوقت المتبقي: " + days + " يوم\n");
        } else {
            remainingText.setText("الوقت المتبقي: غير متاح\n");
        }

        if (info.licensePresent) {
            licenseText.setText("الترخيص: غير صالح\n");
        } else {
            licenseText.setText("الترخيص: غير موجود\n");
        }

        String statusColor = info.trialExpired ? bad : warn;
        applyStyle(statusText, GAFATA, size, statusColor);
        applyStyle(remainingText, GAFATA, size, statusColor);
        applyStyle(licenseText, GAFATA, size, statusColor);
    }
}
