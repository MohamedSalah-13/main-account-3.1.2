package com.hamza.account.controller.setting;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.config.FontManager;
import com.hamza.account.features.events.FontChanged;
import com.hamza.account.features.events.LanguageChanged;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.features.employee.EmployeeService;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.CurrencyFormat;
import com.hamza.account.features.currency.CurrencyService;
import com.hamza.account.features.events.CurrenciesChanged;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.util.*;



@Log4j2
@FxmlPath(pathFile = "include/settingTabLanguage.fxml")
public class SettingTabLanguageController implements Initializable {

    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final CurrencyService currencies = ServiceRegistry.get(CurrencyService.class);
    /** Set while the combo is refilled, so putting the base back in it is not read as a choice. */
    private boolean fillingCurrencies;
    @FXML
    private Button btnAddFont;
    @FXML
    private ComboBox<Currency> comboCurrency;
    @FXML
    private Label labelCurrencyPreview, labelCurrencyNote;
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
    private Label labelFontPreview, labelFontSupport;
    @FXML
    private GridPane preferencesGrid;
    @FXML
    private VBox languageCard, appearanceCard;

    public static String publishDelegate(EmployeeService employeeService) {
        // for employee
        var proEmpl = Integer.parseInt(com.hamza.account.config.PropertiesName.getSettingSaveNameDelegate());
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
        configureCurrencyCombo();
        var current = com.hamza.account.config.ThemeManager.getCurrentTheme();
        radioDark.setSelected(current == com.hamza.account.config.ThemeManager.Theme.DARK);
        radioLight.setSelected(current != com.hamza.account.config.ThemeManager.Theme.DARK);
        radioLight.setOnAction(e -> applyTheme(com.hamza.account.config.ThemeManager.Theme.LIGHT));
        radioDark.setOnAction(e -> applyTheme(com.hamza.account.config.ThemeManager.Theme.DARK));
        configureUiScaleCombo();
        configureLanguageCombo();
        configureFontCombo();
        configureTableAppearance();
        btnAddFont.setGraphic(com.hamza.account.config.AppIcon.ADD.graphic());
        preferencesGrid.widthProperty().addListener((observable, oldWidth, width) -> layoutPreferences(width.doubleValue()));
        layoutPreferences(preferencesGrid.getWidth());
    }

    private void layoutPreferences(double width) {
        boolean stacked = width < 760;
        preferencesGrid.getColumnConstraints().get(0).setPercentWidth(stacked ? 100 : 40);
        preferencesGrid.getColumnConstraints().get(1).setPercentWidth(stacked ? 0 : 60);
        GridPane.setColumnIndex(appearanceCard, stacked ? 0 : 1);
        GridPane.setRowIndex(appearanceCard, stacked ? 1 : 0);
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
     * register a new {@code .ttf}/{@code .otf} file, using the same file chooser as other settings.
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
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(LanguageManager.getInstance().getString("settings.language.fontFiles"), "*.ttf", "*.otf"));
        File file = fc.showOpenDialog(btnAddFont.getScene() == null ? null : btnAddFont.getScene().getWindow());
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
