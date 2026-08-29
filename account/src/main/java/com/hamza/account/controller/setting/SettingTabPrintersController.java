package com.hamza.account.controller.setting;

import com.hamza.account.openFxml.FxmlPath;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.print.Printer;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import java.io.IOException;
import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import static com.hamza.account.config.PropertiesName.getSettingPrinterBarcode;
import static com.hamza.account.config.PropertiesName.getSettingPrinterNormal;
import static com.hamza.account.config.PropertiesName.getSettingPrinterThermal;
import static com.hamza.account.config.PropertiesName.setSettingPrinterBarcode;
import static com.hamza.account.config.PropertiesName.setSettingPrinterNormal;
import static com.hamza.account.config.PropertiesName.setSettingPrinterThermal;

/** Keeps the application's output roles and the Windows default printer visible in one place. */
@FxmlPath(pathFile = "include/settingTabPrinters.fxml")
public class SettingTabPrintersController implements Initializable {

    @FXML private ComboBox<String> comboNormal;
    @FXML private ComboBox<String> comboBarcode;
    @FXML private ComboBox<String> comboThermal;
    @FXML private ComboBox<String> comboSystemDefault;
    @FXML private Label labelStatus;
    @FXML private Label labelDefaultStatus;
    @FXML private TextArea textCapabilities;
    @FXML private Button btnRefresh;
    @FXML private Button btnSetDefault;
    @FXML private Button btnNormalSettings;
    @FXML private Button btnBarcodeSettings;
    @FXML private Button btnThermalSettings;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        wireOutputRole(comboNormal, value -> setSettingPrinterNormal(value));
        wireOutputRole(comboBarcode, value -> setSettingPrinterBarcode(value));
        wireOutputRole(comboThermal, value -> setSettingPrinterThermal(value));
        comboSystemDefault.valueProperty().addListener((observable, oldValue, value) -> showCapabilities(value));
        btnRefresh.setOnAction(event -> refreshPrinters());
        btnSetDefault.setOnAction(event -> setWindowsDefault(comboSystemDefault.getValue()));
        btnNormalSettings.setOnAction(event -> openNativeSettings(comboNormal.getValue()));
        btnBarcodeSettings.setOnAction(event -> openNativeSettings(comboBarcode.getValue()));
        btnThermalSettings.setOnAction(event -> openNativeSettings(comboThermal.getValue()));
        refreshPrinters();
    }

    private void wireOutputRole(ComboBox<String> combo, Consumer<String> writer) {
        combo.valueProperty().addListener((observable, oldValue, value) -> {
            if (value != null && !value.isBlank()) writer.accept(value);
        });
    }

    private void refreshPrinters() {
        List<String> names = Printer.getAllPrinters().stream()
                .map(Printer::getName)
                .sorted(Comparator.naturalOrder())
                .toList();
        setChoices(comboNormal, names, getSettingPrinterNormal());
        setChoices(comboBarcode, names, getSettingPrinterBarcode());
        setChoices(comboThermal, names, getSettingPrinterThermal());
        String defaultName = Printer.getDefaultPrinter() == null ? null : Printer.getDefaultPrinter().getName();
        setChoices(comboSystemDefault, names, defaultName);
        labelStatus.setText(names.isEmpty()
                ? text("settings.printers.noneFound")
                : text("settings.printers.found", names.size()));
        labelDefaultStatus.setText(defaultName == null
                ? text("settings.printers.noDefault")
                : text("settings.printers.currentDefault", defaultName));
        showCapabilities(defaultName);
    }

    private void setChoices(ComboBox<String> combo, List<String> names, String selected) {
        combo.getItems().setAll(names);
        if (selected != null && !selected.isBlank() && !names.contains(selected)) combo.getItems().add(selected);
        combo.setValue(selected);
    }

    private void showCapabilities(String printerName) {
        Printer printer = Printer.getAllPrinters().stream()
                .filter(candidate -> candidate.getName().equals(printerName))
                .findFirst().orElse(null);
        if (printer == null) {
            textCapabilities.setText(text("settings.printers.capabilitiesUnavailable"));
            return;
        }
        var attributes = printer.getPrinterAttributes();
        String papers = attributes.getSupportedPapers().stream().map(Object::toString).sorted().reduce((left, right) -> left + ", " + right).orElse("-");
        String orientations = attributes.getSupportedPageOrientations().stream().map(Object::toString).sorted().reduce((left, right) -> left + ", " + right).orElse("-");
        String resolutions = attributes.getSupportedPrintResolutions().stream().map(Object::toString).sorted().reduce((left, right) -> left + ", " + right).orElse("-");
        textCapabilities.setText(text("settings.printers.capabilities", papers, orientations,
                attributes.getSupportedPrintSides(), attributes.getSupportedPrintColors(),
                attributes.getSupportedPrintQuality(), resolutions));
    }

    private void setWindowsDefault(String printerName) {
        if (printerName == null || printerName.isBlank()) return;
        btnSetDefault.setDisable(true);
        Thread.ofVirtual().start(() -> {
            try {
                Process process = new ProcessBuilder("rundll32", "printui.dll,PrintUIEntry", "/y", "/n", printerName).start();
                int exitCode = process.waitFor();
                Platform.runLater(() -> {
                    btnSetDefault.setDisable(false);
                    if (exitCode == 0) {
                        labelDefaultStatus.setText(text("settings.printers.defaultSet", printerName));
                        refreshPrinters();
                    } else {
                        AllAlerts.handleError(text("settings.printers.defaultContext"), new IOException("Exit code: " + exitCode));
                    }
                });
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                Platform.runLater(() -> {
                    btnSetDefault.setDisable(false);
                    AllAlerts.handleError(text("settings.printers.defaultContext"), e);
                });
            }
        });
    }

    private void openNativeSettings(String printerName) {
        if (printerName == null || printerName.isBlank()) return;
        try {
            new ProcessBuilder("rundll32", "printui.dll,PrintUIEntry", "/e", "/n", printerName).start();
        } catch (IOException e) {
            AllAlerts.handleError(text("settings.printerSettings.context"), e);
        }
    }

    private String text(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }
}