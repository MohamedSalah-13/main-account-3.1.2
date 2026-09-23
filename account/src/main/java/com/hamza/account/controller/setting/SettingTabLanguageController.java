package com.hamza.account.controller.setting;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.PropertiesName;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.controller.search.CustomerSearchController;
import com.hamza.account.controller.search.SearchInterface;
import com.hamza.account.config.FontManager;
import com.hamza.account.features.events.FontChanged;
import com.hamza.account.features.events.LanguageChanged;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.config.NamesTables;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.CustomerService;
import com.hamza.account.features.employee.EmployeeScope;
import com.hamza.account.features.employee.EmployeeService;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.CurrencyFormat;
import com.hamza.account.features.currency.CurrencyService;
import com.hamza.account.features.events.CurrenciesChanged;
import com.hamza.account.view.TableWithTextSearchApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.Columns;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.others.TextFormat;
import com.hamza.controlsfx.util.ImageChoose;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.hamza.account.config.PropertiesName.*;
import static com.hamza.controlsfx.others.Utils.setTextFormatter;


@Log4j2
@FxmlPath(pathFile = "include/settingTabLanguage.fxml")
public class SettingTabLanguageController implements Initializable {

    private final Publisher<String> changeImage;
    private final CustomerService customerService = ServiceRegistry.get(CustomerService.class);
    private final EmployeeService employeeService = ServiceRegistry.get(EmployeeService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final CurrencyService currencies = ServiceRegistry.get(CurrencyService.class);
    /** Set while the combo is refilled, so putting the base back in it is not read as a choice. */
    private boolean fillingCurrencies;
    @FXML
    private Button btnPath, btnDeleteImage;
    @FXML
    private ComboBox<Currency> comboCurrency;
    @FXML
    private Label labelRate, labelLanguage, labelCurrency, labelCurrencyPreview, labelCurrencyNote;
    @FXML
    private Label labelPath;
    @FXML
    private TextField textRateSel, textSerial;
    @FXML
    private Label textPath;
    @FXML
    private RadioButton radioLight, radioDark;
    @FXML
    private ComboBox<Locale> comboLanguage;
    @FXML
    private ComboBox<Double> comboUiScale;
    @FXML
    private ComboBox<String> comboFont;
    @FXML
    private CheckBox checkColumnDividers, checkFillColumns;
    @FXML
    private Button btnAddFont;
    @FXML
    private Label labelFontPreview, labelFontSupport;
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
            return employeeService.delegateById(proEmpl);
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

        clearSetting(btnPath);
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

        btnPath.setOnAction(actionEvent -> getFileChooser());
        // add imagePath
        var text = LanguageManager.getInstance().getString("settings.image.none");
        textPath.setText(getPathImageMainScreen().isEmpty() ? text : getPathImageMainScreen());
        configureCurrencyCombo();

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
        configureTableAppearance();

        btnDeleteImage.setOnAction(actionEvent -> {
            textPath.setText(text);
            setPathImageMainScreen("");
            changeImage.publish("");
        });

        setGraphic();

        textSerial.setText(String.valueOf(getSerialRecordModificationNumber()));
        textSerial.textProperty().addListener((observableValue, s, t1) -> {
            // An emptied field is someone midway through typing, not a failure to log on
            // every keystroke. The stored number stands until the field reads as one.
            if (t1 == null || t1.isBlank()) return;
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
            // The default customer is the shop's - a cash sale must not land in a
            // different account depending on which till served it. The delegate beside it
            // is deliberately not: sharing it would credit every sale to one salesperson.
            SettingScope.shared(txtNameCustomer, com.hamza.account.config.SharedSettingKeys.DEFAULT_CUSTOMER);
        } catch (Exception e) {
            log.error("Failed to publish customer and delegate: {}", e.getMessage());
        }

        btnSaveCustomer.setOnAction(actionEvent -> chooseCustomer());
        btnSaveDelegate.setOnAction(actionEvent -> chooseDelegate());

    }

    private void chooseCustomer() {
        try {
            TableWithTextSearchApplication<Customers> tableWithTextSearchApplication = new TableWithTextSearchApplication<>(
                    // The customer new invoices default to, so a stopped one is not
                    // offered here either.
                    new CustomerSearchController(customerService,
                            com.hamza.account.party.PartyTableSpec.PartySearchScope.ACTIVE_ONLY)
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
                public List<TableColumn<Employees, ?>> columns() {
                    return List.of(
                            Columns.number(NamesTables.CODE, Employees::getId),
                            Columns.text(NamesTables.NAME, Employees::getName),
                            Columns.date(Setting_Language.string_birth, Employees::getBirth_date),
                            Columns.date(Setting_Language.string_hire, Employees::getHire_date),
                            Columns.number(NamesTables.SALARY, Employees::getSalary),
                            Columns.text(NamesTables.EMAIL, Employees::getEmail),
                            Columns.text(NamesTables.TEL, Employees::getTel),
                            Columns.text(NamesTables.ADDRESS, Employees::getAddress)
                    );
                }

                @Override
                public String getName(Employees customers) {
                    return LanguageManager.getInstance().getString("employees");
                }

                @Override
                public List<Employees> getFilterItems(String filter) throws Exception {
                    return employeeService.delegates(EmployeeScope.EVERYONE).stream()
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


    /**
     * The program's currency, chosen here the way its language is chosen above it - and it is the base
     * currency (V80), the one every amount in the books is in, not a second setting beside it.
     * <p>
     * <b>Two differences from the language are the point.</b> The language is this computer's: two tills
     * may read one database in two languages. The currency is the shop's, because it says what the
     * figures in that database are - so it lives in {@code currency.is_base}, every till reads the same
     * row, and a change here is raised as {@code CurrenciesChanged}, which the relay carries to the
     * others. And a language changes freely while the currency changes only through
     * {@link CurrencyService#setBase}, whose rule is that it moves only while no exchange rate is recorded:
     * before that it corrects a wrong label, after it every rate would silently change meaning.
     * <p>
     * The combo offers every active currency only to a user who may set the base and only while it may
     * still move; otherwise it holds the base alone and the line under it says why - in words, since a
     * disabled control never shows its tooltip. Its symbol follows the interface's language
     * ({@link Currency#symbolFor}), which the example line shows.
     */
    private void configureCurrencyCombo() {
        comboCurrency.setTooltip(new Tooltip(LanguageManager.getInstance().getString("settings.currency.hint")));
        comboCurrency.setConverter(new StringConverter<>() {
            @Override
            public String toString(Currency currency) {
                return currency == null ? "" : currency.label();
            }

            @Override
            public Currency fromString(String string) {
                return null;
            }
        });
        if (currencies == null) {
            return;
        }
        fillCurrencies();
        comboCurrency.valueProperty().addListener((observable, oldValue, chosen) -> {
            if (fillingCurrencies || chosen == null || chosen.base()) return;
            // After the popup has closed: a confirmation opened from inside the selection change would
            // sit over a list still being dismissed.
            Platform.runLater(() -> changeProgramCurrency(chosen));
        });
    }

    private void fillCurrencies() {
        try {
            Currency base = currencies.base();
            boolean granted = AuthorizationGuard.isGranted(AppPermissions.CURRENCY_UPDATE);
            boolean mayChange = granted && currencies.baseMayChange();
            List<Currency> choices = mayChange ? currencies.active() : List.of(base);
            fillingCurrencies = true;
            try {
                comboCurrency.getItems().setAll(choices);
                comboCurrency.getSelectionModel().select(choices.stream()
                        .filter(currency -> currency.id() == base.id()).findFirst().orElse(base));
            } finally {
                fillingCurrencies = false;
            }
            String note = mayChange ? "settings.currency.note.open"
                    : granted ? "settings.currency.note.locked" : "settings.currency.note.denied";
            labelCurrencyNote.setText(LanguageManager.getInstance().getString(note));
            showCurrencyExample(base);
        } catch (Exception e) {
            log.warn("Could not read the currencies for the settings tab", e);
        }
    }

    private void changeProgramCurrency(Currency chosen) {
        var languageManager = LanguageManager.getInstance();
        String operation = languageManager.getString("settings.currency.op");
        if (AllAlerts.confirm_all(operation, languageManager.getString("currency.base.confirm", chosen.label()))) {
            try {
                currencies.setBase(chosen.id());
                // The dashboard's and the kiosk's symbol, the currencies screen, and the other tills.
                if (eventBus != null) eventBus.publish(new CurrenciesChanged());
            } catch (Exception e) {
                AllAlerts.handleError(operation, e);
            }
        }
        // Refused, declined or done: the combo says what the database now says.
        fillCurrencies();
    }

    /** "For example: 1,250.00 L.E." - the base's places and its symbol in the interface's language. */
    private void showCurrencyExample(Currency base) {
        String symbol = base.symbolFor(LanguageManager.getInstance().getCurrentLocale());
        String amount = CurrencyFormat.amount(BigDecimal.valueOf(1250), base);
        labelCurrencyPreview.setText(LanguageManager.getInstance().getString("settings.currency.preview",
                symbol.isBlank() ? amount : amount + " " + symbol));
    }

    private void getFileChooser() {
        final FileChooser.ExtensionFilter FILTER_IMAGE = new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.png");
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(FILTER_IMAGE);
        Path path = Path.of(getPathImageMainScreen()).getParent() == null ? Path.of(System.getProperty("user.home")) : Path.of(getPathImageMainScreen()).getParent();

        log.info("path: {}", path);
        var file1 = path.toFile();
        // Only a directory that exists is worth offering, and JavaFX ignores one that
        // does not - so the test used to be inverted and the chooser never opened where
        // the last image came from, however carefully the line above worked it out.
        if (file1.isDirectory()) {
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
            if (eventBus != null) eventBus.publish(new LanguageChanged(newValue));
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
        updateFontPreview(comboFont.getValue());

        comboFont.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.equals(FontManager.getCurrentFamily())) return;
            FontManager.setCurrentFamily(newValue);
            reapplyToCurrentScene();
            updateFontPreview(newValue);
            if (eventBus != null) eventBus.publish(new FontChanged(newValue));
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
        updateFontPreview(family);
        AllAlerts.alertSaveWithMessage(LanguageManager.getInstance().getString("settings.language.fontAdded"));
    }

    private void configureTableAppearance() {
        checkColumnDividers.setSelected(com.hamza.account.config.TableAppearance.showColumnDividers());
        checkFillColumns.setSelected(com.hamza.account.config.TableAppearance.fillAvailableWidth());
        checkColumnDividers.selectedProperty().addListener((observable, oldValue, selected) -> {
            com.hamza.account.config.TableAppearance.setShowColumnDividers(selected);
            reapplyToCurrentScene();
        });
        checkFillColumns.selectedProperty().addListener((observable, oldValue, selected) -> {
            com.hamza.account.config.TableAppearance.setFillAvailableWidth(selected);
            reapplyToCurrentScene();
        });
    }

    private void updateFontPreview(String family) {
        labelFontPreview.setFont(FontManager.previewFont(family));
        labelFontPreview.setText(LanguageManager.getInstance().getString("settings.language.fontPreviewSample"));
        String supportKey = switch (FontManager.arabicSupport(family)) {
            case SUPPORTED -> "settings.language.fontSupportsArabic";
            case NOT_SUPPORTED -> "settings.language.fontDoesNotSupportArabic";
            case UNDETERMINED -> "settings.language.fontArabicSupportUnknown";
        };
        labelFontSupport.setText(LanguageManager.getInstance().getString(supportKey));
    }
    /**
     * The font preview is assembled in code from the chosen family, and the currency's example
     * line from the symbol of the interface's language, so they are the two pieces of this
     * tab's text a language change still has to be told about. The rest are
     * {@code %key} bindings in the FXML now - including the theme label and its radio
     * buttons, which were English literals no code ever replaced - and come back
     * translated when the tab is reopened, which is what {@link
     * com.hamza.account.openFxml.OpenFxmlApplication} re-reading {@link
     * LanguageManager#getResourceBundle()} on every load is for.
     */
    private void refreshOwnText() {
        updateFontPreview(comboFont.getValue());
        // The symbol is written in the interface's language, so the example line changes with it.
        if (currencies != null) fillCurrencies();
    }

    /**
     * Appearance choices are global preferences. Reapply them to every showing
     * window so a font change is visible immediately in the main screen, settings
     * and any currently open dialog.
     */
    private void reapplyToCurrentScene() {
        com.hamza.account.config.ThemeManager.refreshOpenWindows();
    }
}
