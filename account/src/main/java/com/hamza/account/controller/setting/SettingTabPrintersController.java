package com.hamza.account.controller.setting;

import com.hamza.account.openFxml.FxmlPath;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.print.Printer;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.print.Printable;
import java.awt.print.PrinterAbortException;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;

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
    @FXML private Button btnNormalTest;
    @FXML private Button btnBarcodeTest;
    @FXML private Button btnThermalTest;
    @FXML private Label labelNormalMissing;
    @FXML private Label labelBarcodeMissing;
    @FXML private Label labelThermalMissing;

    /**
     * The printers as of the last refresh.
     * <p>
     * {@link Printer#getAllPrinters()} asks the operating system, which on a machine with
     * network printers can take seconds. It used to be called three times per refresh -
     * once for the names, once again because setting the default combo fires the listener
     * that reads capabilities, and once more from the explicit call after - all on the
     * JavaFX thread while the settings screen was being built. Since SettingController
     * builds every tab when settings opens, anyone opening settings for any reason waited
     * for it.
     */
    private List<Printer> printers = List.of();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        wireOutputRole(comboNormal, labelNormalMissing, value -> setSettingPrinterNormal(value));
        wireOutputRole(comboBarcode, labelBarcodeMissing, value -> setSettingPrinterBarcode(value));
        wireOutputRole(comboThermal, labelThermalMissing, value -> setSettingPrinterThermal(value));
        comboSystemDefault.valueProperty().addListener((observable, oldValue, value) -> showCapabilities(value));
        btnRefresh.setOnAction(event -> refreshPrinters());
        btnSetDefault.setOnAction(event -> setWindowsDefault(comboSystemDefault.getValue()));
        btnNormalSettings.setOnAction(event -> openNativeSettings(comboNormal.getValue()));
        btnBarcodeSettings.setOnAction(event -> openNativeSettings(comboBarcode.getValue()));
        btnThermalSettings.setOnAction(event -> openNativeSettings(comboThermal.getValue()));
        btnNormalTest.setOnAction(event -> printTestPage(comboNormal.getValue()));
        btnBarcodeTest.setOnAction(event -> printTestPage(comboBarcode.getValue()));
        btnThermalTest.setOnAction(event -> printTestPage(comboThermal.getValue()));
        refreshPrinters();
    }

    private void wireOutputRole(ComboBox<String> combo, Label missingWarning, Consumer<String> writer) {
        combo.valueProperty().addListener((observable, oldValue, value) -> {
            if (value != null && !value.isBlank()) writer.accept(value);
            markAvailability(combo, missingWarning);
        });
    }

    /**
     * Says so when a role points at a printer the system does not have.
     * <p>
     * This is not cosmetic. CheckPrinterSetting.checkPrinter substitutes "Microsoft Print
     * to PDF" for a printer it cannot find, without a word - so an unplugged or renamed
     * till printer turns every invoice into a PDF, and the cashier sees a print that
     * produced no paper. The screen already knew: setChoices adds the missing name to the
     * list so the combo can still show it, and then said nothing about it.
     */
    private void markAvailability(ComboBox<String> combo, Label missingWarning) {
        String selected = combo.getValue();
        boolean missing = selected != null && !selected.isBlank()
                && printers.stream().noneMatch(printer -> printer.getName().equals(selected));
        missingWarning.setText(missing ? text("settings.printers.missing") : "");
        missingWarning.setVisible(missing);
        missingWarning.setManaged(missing);
    }

    /**
     * Asks the operating system for its printers once, off the JavaFX thread, and hands
     * the answer to the screen. Everything else here reads the cached list.
     */
    private void refreshPrinters() {
        labelStatus.setText(text("settings.printers.loading"));
        btnRefresh.setDisable(true);
        Thread.ofVirtual().start(() -> {
            List<Printer> found = Printer.getAllPrinters().stream()
                    .sorted(Comparator.comparing(Printer::getName))
                    .toList();
            Printer systemDefault = Printer.getDefaultPrinter();
            String defaultName = systemDefault == null ? null : systemDefault.getName();
            Platform.runLater(() -> applyPrinters(found, defaultName));
        });
    }

    private void applyPrinters(List<Printer> found, String defaultName) {
        printers = found;
        btnRefresh.setDisable(false);
        List<String> names = found.stream().map(Printer::getName).toList();

        setChoices(comboNormal, names, getSettingPrinterNormal());
        setChoices(comboBarcode, names, getSettingPrinterBarcode());
        setChoices(comboThermal, names, getSettingPrinterThermal());
        setChoices(comboSystemDefault, names, defaultName);

        markAvailability(comboNormal, labelNormalMissing);
        markAvailability(comboBarcode, labelBarcodeMissing);
        markAvailability(comboThermal, labelThermalMissing);

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

    /**
     * Sends one page to the printer assigned to a role, so the assignment can be proved
     * before a customer is standing at the counter waiting for it.
     * <p>
     * Deliberately plain ASCII: the point is that paper comes out of the right device, and
     * a thermal roll rendering Arabic through AWT is a second thing that can fail and
     * confuse the first.
     */
    private void printTestPage(String printerName) {
        if (printerName == null || printerName.isBlank()) {
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                PrintService service = Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                        .filter(candidate -> candidate.getName().equals(printerName))
                        .findFirst()
                        .orElse(null);
                if (service == null) {
                    Platform.runLater(() -> AllAlerts.handleError(text("settings.printers.testContext"),
                            new UserValidationException(text("settings.printers.testNoPrinter", printerName))));
                    return;
                }
                PrinterJob job = PrinterJob.getPrinterJob();
                job.setPrintService(service);
                job.setPrintable(testPagePrintable(printerName));
                job.print();
                Platform.runLater(() -> labelStatus.setText(text("settings.printers.testSent", printerName)));
            } catch (PrinterAbortException cancelled) {
                // Closing the print dialog - the save box "Microsoft Print to PDF" puts up,
                // for instance - arrives here. Someone changing their mind is not a failure
                // to report behind a reference code.
                Platform.runLater(() -> labelStatus.setText(text("settings.printers.testCancelled")));
            } catch (Exception e) {
                Platform.runLater(() -> AllAlerts.handleError(text("settings.printers.testContext"), e));
            }
        });
    }

    private Printable testPagePrintable(String printerName) {
        List<String> lines = List.of(
                "Test page",
                "Printer : " + printerName,
                "Time    : " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                "",
                "1234567890  ABCDEFGHIJ",
                "If you can read this, the printer works.");
        return (graphics, pageFormat, pageIndex) -> {
            if (pageIndex > 0) {
                return Printable.NO_SUCH_PAGE;
            }
            Graphics2D canvas = (Graphics2D) graphics;
            canvas.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
            canvas.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
            int y = 14;
            for (String line : lines) {
                canvas.drawString(line, 0, y);
                y += 14;
            }
            return Printable.PAGE_EXISTS;
        };
    }

    private void showCapabilities(String printerName) {
        Printer printer = printers.stream()
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