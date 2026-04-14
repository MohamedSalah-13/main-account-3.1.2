package com.hamza.account.controller.setting;

import com.hamza.account.choiceDialoge.ChoiceDialogSetting;
import com.hamza.account.choiceDialoge.ChoosePrinter;
import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.PropertiesName;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.controller.search.SearchInterface;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.CustomerService;
import com.hamza.account.service.EmployeeService;
import com.hamza.account.view.TableWithTextSearchApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.file.Extensions;
import com.hamza.controlsfx.util.ImageChoose;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.others.TextFormat;
import javafx.collections.ObservableSet;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.print.Printer;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

import static com.hamza.account.config.PropertiesName.*;
import static com.hamza.account.otherSetting.Currency_Setting.getCurrency;
import static com.hamza.account.otherSetting.Currency_Setting.listOfCurrency2;
import static com.hamza.controlsfx.others.Utils.setTextFormatter;


@Log4j2
@FxmlPath(pathFile = "include/settingTabLanguage.fxml")
public class SettingTabLanguageController extends ServiceData implements Initializable {

    private final Publisher<String> changeImage;
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
    private RadioButton radioEnglish, radioArabic;
    @FXML
    private TextField txtNameCustomer, txtNameDelegate;
    @FXML
    private Button btnSaveCustomer, btnSaveDelegate;

    public SettingTabLanguageController(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory);
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

        labelRate.setText(Setting_Language.WORD_RATE);
        labelLanguage.setText(Setting_Language.WORD_LANGUAGE);

        labelPrintNormal.setText(Setting_Language.print1);
        labelPrintBarcode.setText(Setting_Language.print2);
        labelPrintOther.setText(Setting_Language.print3);
        labelPath.setText("صورة الخلفية");
        labelCurrency.setText(Setting_Language.THE_CURRENCY);

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
        var text = "لا يوجد صورة";
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

        btnDeleteImage.setOnAction(actionEvent -> {
            textPath.setText(text);
            setPathImageMainScreen("");
            changeImage.setAvailability("");
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
            TableWithTextSearchApplication<Customers> tableWithTextSearchApplication = new TableWithTextSearchApplication<>(new SearchInterface<>() {
                @Override
                public Class<? super Customers> getSearchClass() {
                    return BaseNames.class;
                }

                @Override
                public List<Customers> searchItems() throws DaoException {
                    return customerService.getCustomerList();
                }

                @Override
                public String getName(Customers customers) {
                    return Setting_Language.WORD_CUSTOM;
                }
            });
            Optional<Customers> customers = tableWithTextSearchApplication.showAndWait();
            customers.ifPresent(itemsModel -> {
                setSettingSaveNameCustomer(String.valueOf(itemsModel.getId()));
                txtNameCustomer.setText(itemsModel.getName());
            });
        } catch (IOException e) {
            log.error("Failed to open search customer dialog: {}", e.getMessage());
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
                public List<Employees> searchItems() throws Exception {
                    return employeeService.getDelegateList();
                }

                @Override
                public String getName(Employees customers) {
                    return Setting_Language.EMPLOYEES;
                }

            });
            Optional<Employees> customers = tableWithTextSearchApplication.showAndWait();
            customers.ifPresent(itemsModel -> {
                txtNameDelegate.setText(itemsModel.getName());
                setSettingSaveNameDelegate(String.valueOf(itemsModel.getId()));
            });
        } catch (IOException e) {
            log.error("Failed to open search delegate dialog: {}", e.getMessage());
        }
    }

    private void openSettingPrinter(String printerName) {
        try {
            Runtime.getRuntime().exec("rundll32 printui.dll,PrintUIEntry /e /n \"" + printerName + "\"");
        } catch (IOException e) {
            log.error("Failed to open printer settings: {}", e.getMessage());
            AllAlerts.showExceptionDialog(e);
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
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(Extensions.FILTER_IMAGE);
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
            changeImage.setAvailability(absolutePath);
        }
    }

    private void applyTheme(com.hamza.account.config.ThemeManager.Theme theme) {
        // Persist selection
        com.hamza.account.config.ThemeManager.setCurrentTheme(theme);
        // Apply to current scene
        var scene = labelLanguage.getScene();
        if (scene != null) {
            com.hamza.account.config.ThemeManager.apply(scene);
            // keep legacy font/color loading and responsive font size
            com.hamza.account.config.Style_Sheet.changeStyle(scene);
        } else if (labelLanguage.getParent() != null) {
            com.hamza.account.config.ThemeManager.apply(labelLanguage.getParent());
        }
    }
}
