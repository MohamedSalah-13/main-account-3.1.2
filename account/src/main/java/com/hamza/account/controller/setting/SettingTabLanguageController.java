package com.hamza.account.controller.setting;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.PropertiesName;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.controller.search.CustomerSearchController;
import com.hamza.account.controller.search.SearchInterface;
import com.hamza.account.features.choiceDialoge.ChoiceDialogSetting;
import com.hamza.account.features.choiceDialoge.ChoosePrinter;
import com.hamza.account.config.FontManager;
import com.hamza.account.features.events.FontChanged;
import com.hamza.account.features.events.LanguageChanged;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.CustomerService;
import com.hamza.account.service.EmployeeService;
import com.hamza.account.view.TableWithTextSearchApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.others.TextFormat;
import com.hamza.controlsfx.util.ImageChoose;
import javafx.collections.ObservableSet;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.print.Printer;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.hamza.account.config.PropertiesName.*;
import static com.hamza.account.otherSetting.Currency_Setting.getCurrency;
import static com.hamza.account.otherSetting.Currency_Setting.listOfCurrency2;
import static com.hamza.controlsfx.others.Utils.setTextFormatter;


@Log4j2
@FxmlPath(pathFile = "include/settingTabLanguage.fxml")
public class SettingTabLanguageController implements Initializable {

    private final Publisher<String> changeImage;
    private final CustomerService customerService = ServiceRegistry.get(CustomerService.class);
    private final EmployeeService employeeService = ServiceRegistry.get(EmployeeService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    @FXML
    private Button btnPrintNormal, btnPrintBarcode, btnPrintOther, btnPath, btnPrinterSettingNormal, btnPrinterSettingBarcode, btnPrinterSettingOther, btnDeleteImage;
    @FXML
    private ComboBox<String> comboCurrency;
    @FXML
    private Label labelRate, labelLanguage, labelCurrency;
    @FXML
    private Label labelPrintNormal, labelPrintBarcode, labelPrintOther, labelPath;
    @FXML
    private TextField textPrintNormal, textPrintBarcode, textPrintOther;
    @FXML
    private TextField textRateSel, textSerial;
    @FXML
    private Label textPath;
    @FXML
    private RadioButton radioLight, radioDark, radioSystem;
    @FXML
    private ComboBox<Locale> comboLanguage;
    @FXML
    private ComboBox<Double> comboUiScale;
    @FXML
    private ComboBox<String> comboFont;
    @FXML
    private Button btnAddFont;
    @FXML
    private TextField txtNameCustomer, txtNameDelegate;
    @FXML
    private Button btnSaveCustomer, btnSaveDelegate;

    public SettingTabLanguageController(DataPublisher dataPublisher) {
        this.changeImage = dataPublisher.getChangeMainScreenImage();
    }

    public static String publishCustomer(CustomerService customerService) throws Exception {
        var customerById = customerService.getCustomerById(Integer.parseInt(PropertiesName.getSettingSaveNameCustomer()));
        if (customerById == null) {
            return customerService.getCustomerById(1).getName();
        }
        return customerById.getName();
    }

    public static String publishDelegate(EmployeeService employeeService) {
        // for employee
        var proEmpl = Integer.parseInt(getSettingSaveNameDelegate());
        var employeeById = getDelegateById(employeeService, proEmpl);
        if (employeeById == null) {
            return getDelegateById(employeeService, 1).getName();
        }
        return employeeById.getName();
    }

    private static Employees getDelegateById(EmployeeService employeeService, int proEmpl) {
        try {
            return employeeService.getDelegateById(proEmpl);
        } catch (DaoException e) {
            log.error("Failed to get delegate by id: {}", e.getMessage());
            return new Employees(1);
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        otherSetting();
        saveCustomerAndDelegate();
    }

    private void setGraphic() {
        var imageSetting = new Image_Setting();
        btnDeleteImage.setGraphic(ImageChoose.createIcon(imageSetting.erase));
        btnPrinterSettingOther.setGraphic(ImageChoose.createIcon(new Image_Setting().setting));
        btnPrinterSettingBarcode.setGraphic(ImageChoose.createIcon(new Image_Setting().setting));
        btnPrinterSettingNormal.setGraphic(ImageChoose.createIcon(new Image_Setting().setting));

        clearSetting(btnPath);
        clearSetting(btnPrintNormal);
        clearSetting(btnPrintBarcode);
        clearSetting(btnPrintOther);
        clearSetting(btnSaveCustomer);
        clearSetting(btnSaveDelegate);
    }

    private void clearSetting(Button btnPath) {
        btnPath.setText("");
        btnPath.setGraphic(ImageChoose.createIcon(new Image_Setting().details));
    }

    private void otherSetting() {
        setTextFormatter(textRateSel);
        textSerial.setTextFormatter(TextFormat.createNumericTextFormatter());

        btnDeleteImage.setText("");
        btnPrinterSettingOther.setText("");
        btnPrinterSettingBarcode.setText("");
        btnPrinterSettingNormal.setText("");

        labelRate.setText(LanguageManager.getInstance().getString("rate"));
        labelLanguage.setText(LanguageManager.getInstance().getString("settings.language"));

        labelPrintNormal.setText(LanguageManager.getInstance().getString("settings.printer.normal"));
        labelPrintBarcode.setText(LanguageManager.getInstance().getString("settings.printer.barcode"));
        labelPrintOther.setText(LanguageManager.getInstance().getString("settings.printer.invoice"));
        labelPath.setText(LanguageManager.getInstance().getString("settings.image.pathLabel"));
        labelCurrency.setText(LanguageManager.getInstance().getString("settings.currency"));

        textPrintNormal.setText(getSettingPrinterNormal());
        textPrintBarcode.setText(getSettingPrinterBarcode());
        textPrintOther.setText(getSettingPrinterThermal());

        btnPrintNormal.setOnAction(actionEvent -> savePrint(textPrintNormal));
        btnPrintBarcode.setOnAction(actionEvent -> savePrint(textPrintBarcode));
        btnPrintOther.setOnAction(actionEvent -> savePrint(textPrintOther));
        btnPath.setOnAction(actionEvent -> getFileChooser());

        btnPrinterSettingNormal.setOnAction(actionEvent -> openSettingPrinter(textPrintNormal.getText()));
        btnPrinterSettingBarcode.setOnAction(actionEvent -> openSettingPrinter(textPrintBarcode.getText()));
        btnPrinterSettingOther.setOnAction(actionEvent -> openSettingPrinter(textPrintOther.getText()));
        // add imagePath
        var text = LanguageManager.getInstance().getString("settings.image.none");
        textPath.setText(getPathImageMainScreen().isEmpty() ? text : getPathImageMainScreen());
        chooseCurrency();
        radioSystem.setDisable(true);

        // Theme selection: initialize and wire listeners
        try {
            var current = com.hamza.account.config.ThemeManager.getCurrentTheme();
            if (current == com.hamza.account.config.ThemeManager.Theme.DARK) {
                radioDark.setSelected(true);
            } else {
                radioLight.setSelected(true);
            }
        } catch (Exception ignored) {
        }

        radioLight.setOnAction(e -> applyTheme(com.hamza.account.config.ThemeManager.Theme.LIGHT));
        radioDark.setOnAction(e -> applyTheme(com.hamza.account.config.ThemeManager.Theme.DARK));

        // UI scale selection: initialize and wire listeners
        configureUiScaleCombo();

        // Language selection: initialize and wire listeners
        configureLanguageCombo();

        // Font selection: initialize and wire listeners
        configureFontCombo();

        btnDeleteImage.setOnAction(actionEvent -> {
            textPath.setText(text);
            setPathImageMainScreen("");
            changeImage.publish("");
        });

        setGraphic();

        textSerial.setText(String.valueOf(getSerialRecordModificationNumber()));
        textSerial.textProperty().addListener((observableValue, s, t1) -> {
            try {
                setSerialRecordModificationNumber(Integer.parseInt(t1));
            } catch (NumberFormatException e) {
                log.error("Failed to set serial number: {}", e.getMessage());
            }
        });
    }

    private void saveCustomerAndDelegate() {

        try {
            txtNameCustomer.setText(publishCustomer(customerService));
            txtNameDelegate.setText(publishDelegate(employeeService));
        } catch (Exception e) {
            log.error("Failed to publish customer and delegate: {}", e.getMessage());
        }

        btnSaveCustomer.setOnAction(actionEvent -> chooseCustomer());
        btnSaveDelegate.setOnAction(actionEvent -> chooseDelegate());

    }

    private void chooseCustomer() {
        try {
            TableWithTextSearchApplication<Customers> tableWithTextSearchApplication = new TableWithTextSearchApplication<>(
                    new CustomerSearchController(customerService)
            );
            Optional<Customers> customers = tableWithTextSearchApplication.showAndWait();
            customers.ifPresent(itemsModel -> {
                setSettingSaveNameCustomer(String.valueOf(itemsModel.getId()));
                txtNameCustomer.setText(itemsModel.getName());
            });
        } catch (IOException e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("settings.customerSearch.context"), e);
        }
    }

    private void chooseDelegate() {
        try {

            TableWithTextSearchApplication<Employees> tableWithTextSearchApplication = new TableWithTextSearchApplication<>(new SearchInterface<>() {
                @Override
                public Class<? super Employees> getSearchClass() {
                    return Employees.class;
                }

                @Override
                public String getName(Employees customers) {
                    return LanguageManager.getInstance().getString("employees");
                }

                @Override
                public List<Employees> getFilterItems(String filter) throws Exception {
                    return employeeService.getDelegateList().stream()
                            .filter(employee -> employee.getName().toLowerCase().contains(filter.toLowerCase()))
                            .collect(Collectors.toList());
                }
            });
            Optional<Employees> customers = tableWithTextSearchApplication.showAndWait();
            customers.ifPresent(itemsModel -> {
                txtNameDelegate.setText(itemsModel.getName());
                setSettingSaveNameDelegate(String.valueOf(itemsModel.getId()));
            });
        } catch (IOException e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("settings.delegateSearch.context"), e);
        }
    }

    private void openSettingPrinter(String printerName) {
        try {
            // Passed as separate arguments rather than one command line. exec(String)
            // splits on whitespace and keeps the quotes as literal characters, so a
            // printer whose name contains a space was never reached; ProcessBuilder
            // hands each argument over whole and needs no quoting.
            new ProcessBuilder("rundll32", "printui.dll,PrintUIEntry", "/e", "/n", printerName).start();
        } catch (IOException e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("settings.printerSettings.context"), e);
        }
    }


    private void chooseCurrency() {
        List<Map.Entry<Locale, Currency>> entries = listOfCurrency2().stream()
                .filter(localeCurrencyEntry -> localeCurrencyEntry.getKey().getLanguage().contains("ar"))
                .toList();

        for (Map.Entry<Locale, Currency> entry : entries) {
            comboCurrency.getItems().add(entry.getValue().getDisplayName(entry.getKey()));
        }

        comboCurrency.valueProperty().addListener((observableValue, s, t1) -> {
            Optional<Locale> first = entries.stream()
                    .filter(localeCurrencyEntry -> localeCurrencyEntry.getValue().getDisplayName(localeCurrencyEntry.getKey()).equals(t1))
                    .map(Map.Entry::getKey)
                    .findFirst();
            first.ifPresent(locale -> setSettingCurrency(locale.toString()));
        });

        String currency1 = getCurrency()
                .map(localeCurrencyEntry -> localeCurrencyEntry.getValue().getDisplayName(localeCurrencyEntry.getKey())).orElse(null);
        if (currency1 == null) {
            comboCurrency.getSelectionModel().clearSelection();
        } else {
            comboCurrency.getSelectionModel().select(currency1);
        }
    }

    private void savePrint(TextField field) {
        ObservableSet<Printer> printers = javafx.print.Printer.getAllPrinters();
        Optional<String> stringOptional = new ChoiceDialogSetting(printers.stream().map(Printer::getName).toList(), new ChoosePrinter()).showAndWait();
        stringOptional.ifPresent(book -> {
            field.setText(book);
            if (field.equals(textPrintNormal)) {
                setSettingPrinterNormal(book);
            }
            if (field.equals(textPrintBarcode)) {
                setSettingPrinterBarcode(book);
            }
            if (field.equals(textPrintOther)) {
                setSettingPrinterThermal(book);
            }
        });
    }

    private void getFileChooser() {
        final FileChooser.ExtensionFilter FILTER_IMAGE = new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.png");
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(FILTER_IMAGE);
        Path path = Path.of(getPathImageMainScreen()).getParent() == null ? Path.of(System.getProperty("user.home")) : Path.of(getPathImageMainScreen()).getParent();

        log.info("path: {}", path);
        var file1 = path.toFile();
        if (!file1.exists()) {
            fc.setInitialDirectory(file1);
        }
        File file = fc.showOpenDialog(null);
        if (file != null) {
            String absolutePath = file.getAbsolutePath();
            textPath.setText(absolutePath);
            setPathImageMainScreen(absolutePath);
            changeImage.publish(absolutePath);
        }
    }

    private void applyTheme(com.hamza.account.config.ThemeManager.Theme theme) {
        // Persist selection
        com.hamza.account.config.ThemeManager.setCurrentTheme(theme);
        reapplyToCurrentScene();
    }

    /**
     * Same combo-box-of-percentages pattern as center-management's UiScaleSelector:
     * items are the raw factors, and the converter is what turns 1.15 into "115%".
     */
    private void configureUiScaleCombo() {
        comboUiScale.setConverter(new StringConverter<>() {
            @Override
            public String toString(Double value) {
                return value == null ? "" : com.hamza.account.config.UiScale.label(value);
            }

            @Override
            public Double fromString(String string) {
                return null;
            }
        });

        comboUiScale.getItems().clear();
        for (double level : com.hamza.account.config.UiScale.LEVELS) {
            comboUiScale.getItems().add(level);
        }
        comboUiScale.setValue(com.hamza.account.config.UiScale.factor());

        comboUiScale.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.equals(com.hamza.account.config.UiScale.factor())) return;
            com.hamza.account.config.UiScale.setFactor(newValue);
            reapplyToCurrentScene();
        });
    }

    /**
     * Combo box of every language {@link LanguageManager#supportedLocales()} finds on
     * the classpath - unlike the theme radios this needs no code change to grow past
     * two options, since a new {@code messages_xx.properties} is picked up automatically.
     */
    private void configureLanguageCombo() {
        var languageManager = LanguageManager.getInstance();

        comboLanguage.setConverter(new StringConverter<>() {
            @Override
            public String toString(Locale locale) {
                return locale == null ? "" : languageManager.displayNameOf(locale);
            }

            @Override
            public Locale fromString(String string) {
                return null;
            }
        });

        comboLanguage.getItems().setAll(languageManager.supportedLocales());
        comboLanguage.setValue(languageManager.getCurrentLocale());

        comboLanguage.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.equals(languageManager.getCurrentLocale())) return;
            languageManager.setLocale(newValue);
            reapplyToCurrentScene();
            eventBus.publish(new LanguageChanged(newValue));
            refreshOwnText();
        });
    }

    /**
     * Combo box of every family {@link FontManager#allFamilies()} knows about - the
     * bundled fonts plus whatever the user has added - with a button beside it to
     * register a new {@code .ttf}/{@code .otf} file, mirroring {@link #getFileChooser()}.
     */
    private void configureFontCombo() {
        comboFont.getItems().setAll(FontManager.allFamilies());
        comboFont.setValue(FontManager.getCurrentFamily());

        comboFont.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.equals(FontManager.getCurrentFamily())) return;
            FontManager.setCurrentFamily(newValue);
            reapplyToCurrentScene();
            eventBus.publish(new FontChanged(newValue));
        });

        btnAddFont.setOnAction(actionEvent -> addFont());
    }

    private void addFont() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Font Files", "*.ttf", "*.otf"));
        File file = fc.showOpenDialog(null);
        if (file == null) return;

        String family = FontManager.addCustomFont(file);
        if (family == null) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("settings.language.fontAddError"),
                    new IllegalArgumentException(file.getAbsolutePath()));
            return;
        }

        comboFont.getItems().setAll(FontManager.allFamilies());
        comboFont.setValue(family);
        AllAlerts.alertSaveWithMessage(LanguageManager.getInstance().getString("settings.language.fontAdded"));
    }

    /**
     * This tab's other labels are read from {@link LanguageManager} too, but only once,
     * in {@code otherSetting()} at load time - so, unlike {@code labelLanguage}, they do
     * not flip with the combo box until the tab is reopened. Screens built entirely from
     * {@code %key} FXML bindings (login, and future migrated screens) need no equivalent
     * method at all - they reload through {@link
     * com.hamza.account.openFxml.OpenFxmlApplication}, which re-reads {@link
     * LanguageManager#getResourceBundle()} on every load.
     */
    private void refreshOwnText() {
        labelLanguage.setText(LanguageManager.getInstance().getString("settings.language"));
    }

    /**
     * Re-runs {@code ThemeManager.apply} on whichever scene this tab is currently
     * showing in, which stamps both the theme stylesheet and the {@link
     * com.hamza.account.config.UiScale} font size - the tab itself lives in the same
     * tab-paned scene as the sidebar, so both take effect on the whole app immediately.
     */
    private void reapplyToCurrentScene() {
        var scene = labelLanguage.getScene();
        if (scene != null) {
            com.hamza.account.config.ThemeManager.apply(scene);
        } else if (labelLanguage.getParent() != null) {
            com.hamza.account.config.ThemeManager.apply(labelLanguage.getParent());
        }
    }
}
