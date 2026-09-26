package com.hamza.account.controller.convert_treasury;

import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.CurrencyFormat;
import com.hamza.account.features.currency.CurrencyService;
import com.hamza.account.features.currency.RateInForce;
import com.hamza.account.features.events.TreasuriesChanged;
import com.hamza.account.features.events.TreasuryMovementRecorded;
import com.hamza.account.features.events.TreasuryBalancesChanged;
import com.hamza.account.features.events.InvoiceSaved;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.features.treasury.TreasuryExchange;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.model.domain.Users;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.TreasuryBalanceService;
import com.hamza.account.service.TreasuryService;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.account.treasury.TreasuryType;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * Adds and edits treasuries, and is the one screen that shows what each one holds.
 * <p>
 * Two things it deliberately keeps apart, because they were the same word until
 * now: the <b>opening balance</b> is a value the user types
 * ({@code treasury.amount}), and the <b>current balance</b> is derived from every
 * document, deposit and transfer that names the treasury
 * ({@code treasury_current_balance}). The form edits the first; the table shows
 * both. Nothing in the application writes the second.
 * <p>
 * The screen also had no way in at all - {@code treasuryView.fxml} named this
 * controller and no Java loaded it - so a user could not create a second treasury
 * however many the schema supported. {@code TreasuryButtons.treasuries()} opens it.
 */
@FxmlPath(pathFile = "treasury/treasuryView.fxml")
public class TreasuryController {

    @FXML
    private TextField nameField;

    @FXML
    private TextField amountField;

    @FXML
    private ComboBox<TreasuryType> typeCombo;

    @FXML
    private CheckBox activeCheck;

    /**
     * What an e-wallet keeps out of a collection, as a percentage. Zero for a cash
     * drawer, which is what every treasury holds until somebody types otherwise - the
     * collection screen hides its fee row entirely while this is zero.
     */
    @FXML
    private TextField feeField;

    /** The wallet's number or the bank account's - blank for a drawer. */
    @FXML
    private TextField accountField;

    /** What the treasury is warned under; blank or zero is no warning at all. */
    @FXML
    private TextField minimumField;

    /** Where the treasury sits in every picker: lowest first, ties by number. */
    @FXML
    private TextField sortField;

    /**
     * The treasury's currency (V81): the base for every treasury until somebody chooses otherwise. A
     * treasury in a foreign currency takes its opening balance in that currency, valued at the rate of
     * its opening day; the service refuses a change of currency once anything has moved through it.
     */
    @FXML
    private ComboBox<Currency> currencyCombo;

    @FXML
    private Label openingHint;

    @FXML
    private TableView<TreasuryBalanceSummary> treasuryTable;

    @FXML
    private Button newButton;

    @FXML
    private Button saveButton;

    @FXML
    private Button updateButton;

    @FXML
    private Button refreshButton;

    @FXML
    private Button printButton;

    @FXML
    private BorderPane root;

    @FXML
    private VBox treasuryForm;

    @FXML
    private Button formToggleButton;

    private final TreasuryService treasuryService;
    private final TreasuryBalanceService balanceService;
    private final EventBus eventBus;
    private final CurrencyService currencies = ServiceRegistry.get(CurrencyService.class);
    private final Subscriptions subscriptions = new Subscriptions();
    /** Read with the rows, so every cell of one load is valued at one day's rates. */
    private Map<Integer, Currency> currencyById = Map.of();
    private Map<Integer, RateInForce> ratesToday = Map.of();

    private Treasury selectedTreasury;

    public TreasuryController(DaoFactory daoFactory) {
        this.treasuryService = new TreasuryService(daoFactory);
        this.balanceService = new TreasuryBalanceService(daoFactory);
        this.eventBus = ServiceRegistry.get(EventBus.class);
    }

    @FXML
    private void initialize() {
        updateTreasuryFormToggle(true);
        typeCombo.setItems(FXCollections.observableArrayList(TreasuryType.values()));
        typeCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(TreasuryType type) {
                return type == null ? "" : text(type.labelKey());
            }

            @Override
            public TreasuryType fromString(String value) {
                return typeCombo.getValue();
            }
        });
        typeCombo.getSelectionModel().select(TreasuryType.CASH);

        setTextFormatter(amountField, feeField, minimumField);
        configureCurrencyCombo();
        configureButtons();
        whenEnterPressed(nameField, amountField, typeCombo, activeCheck, accountField, minimumField,
                sortField, currencyCombo, feeField);
        // The last field lands on the button that matches the form: editing a selected
        // treasury must not put focus on "save", which would insert a copy of it.
        feeField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                (selectedTreasury == null ? saveButton : updateButton).requestFocus();
            }
        });
        buildColumns();

        treasuryTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> fillForm(newValue));

        loadTreasuries();

        // A deposit, a withdrawal or a transfer entered on another screen changes what
        // this table is showing. The handle is closed with the window rather than left
        // registered on a process-wide bus - see the events section of CLAUDE.md.
        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(TreasuryMovementRecorded.class,
                    event -> loadTreasuries()));
            subscriptions.add(eventBus.subscribe(TreasuryBalancesChanged.class,
                    event -> loadTreasuries()));
            subscriptions.add(eventBus.subscribe(InvoiceSaved.class,
                    event -> loadTreasuries()));
        }
        subscriptions.disposeWith(root);
    }

    private void buildColumns() {
        treasuryTable.getColumns().setAll(
                Columns.number("treasury.column.code", TreasuryBalanceSummary::id),
                Columns.text("treasury.column.name", TreasuryBalanceSummary::name),
                Columns.text("treasury.column.type", row -> text(row.type().labelKey())),
                Columns.text("treasury.column.state", row -> text(row.active()
                        ? "treasury.state.active"
                        : "treasury.state.closed")),
                Columns.money("treasury.column.opening", TreasuryBalanceSummary::opening),
                Columns.money("treasury.column.in", TreasuryBalanceSummary::totalIn),
                Columns.money("treasury.column.out", TreasuryBalanceSummary::totalOut),
                Columns.money("treasury.column.balance", TreasuryBalanceSummary::balance),
                // A treasury in a foreign currency (V81): what it holds in it, what that is worth at
                // today's rate, and how far that is from the book value above - shown, never posted
                // (docs/currency-plan.md §11 ق-ب٢). Blank for a treasury in the base, where the three
                // would only repeat the balance.
                Columns.text("treasury.column.currency", this::currencyCode),
                Columns.text("treasury.column.balance.own", this::ownBalance),
                Columns.money("treasury.column.value.today", this::valueToday),
                Columns.money("treasury.column.valuation", this::valuationDifference),
                // A percentage, not an amount: no money formatting, no red for a negative.
                Columns.number("treasury.column.fee", TreasuryBalanceSummary::feePercent));
    }

    private void configureCurrencyCombo() {
        currencyCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Currency currency) {
                return currency == null ? "" : currency.label();
            }

            @Override
            public Currency fromString(String value) {
                return currencyCombo.getValue();
            }
        });
        currencyCombo.valueProperty().addListener((obs, oldValue, currency) -> showOpeningHint(currency));
        if (currencies == null) {
            currencyCombo.setDisable(true);
            return;
        }
        try {
            currencyCombo.setItems(FXCollections.observableArrayList(currencies.active()));
            selectCurrency(null);
        } catch (DaoException e) {
            AllAlerts.handleError(text("treasury.error.load.title"), e);
        }
    }

    /** {@code null} is the base - the first of the list the service answers. */
    private void selectCurrency(Integer currencyId) {
        currencyCombo.getItems().stream()
                .filter(currency -> currencyId == null ? currency.base() : currency.id() == currencyId)
                .findFirst()
                .ifPresentOrElse(currencyCombo::setValue, () -> {
                    // A treasury in a currency stopped since: shown, so the form does not turn it into the base.
                    Currency stopped = currencyById.get(currencyId);
                    if (stopped != null) {
                        currencyCombo.getItems().add(stopped);
                        currencyCombo.setValue(stopped);
                    }
                });
    }

    private void showOpeningHint(Currency currency) {
        boolean foreign = currency != null && !currency.base();
        openingHint.setText(foreign ? text("treasury.hint.opening.foreign", currency.code())
                : text("treasury.hint.opening"));
    }

    private boolean isForeign() {
        Currency currency = currencyCombo.getValue();
        return currency != null && !currency.base();
    }

    private String currencyCode(TreasuryBalanceSummary row) {
        Currency currency = row.isForeign() ? currencyById.get(row.currencyId()) : null;
        return currency == null ? "" : currency.code();
    }

    private String ownBalance(TreasuryBalanceSummary row) {
        Currency currency = row.isForeign() ? currencyById.get(row.currencyId()) : null;
        return currency == null ? "" : CurrencyFormat.amount(row.balanceOwn(), currency) + " " + currency.code();
    }

    /** What the treasury's own balance is worth at today's rate; blank without a rate - never a guess. */
    private BigDecimal valueToday(TreasuryBalanceSummary row) {
        RateInForce rate = row.isForeign() ? ratesToday.get(row.currencyId()) : null;
        return rate == null ? null : TreasuryExchange.baseOf(row.balanceOwn(), rate.rate());
    }

    private BigDecimal valuationDifference(TreasuryBalanceSummary row) {
        BigDecimal value = valueToday(row);
        return value == null ? null : value.subtract(row.balance());
    }

    /** The list uses the same PDF path as customers, so the visible columns are the printed columns. */
    private void configureButtons() {
        newButton.setGraphic(AppIcon.ADD.graphic());
        saveButton.setGraphic(AppIcon.SAVE.graphic());
        updateButton.setGraphic(AppIcon.EDIT.graphic());
        refreshButton.setGraphic(AppIcon.REFRESH.graphic());
        printButton.setGraphic(AppIcon.PRINT.graphic());
    }

    @FXML
    private void loadTreasuries() {
        try {
            if (currencies != null) {
                currencyById = currencies.all().stream()
                        .collect(java.util.stream.Collectors.toMap(Currency::id, currency -> currency));
                ratesToday = currencies.ratesInForce(LocalDate.now());
            }
            treasuryTable.setItems(FXCollections.observableArrayList(balanceService.getTreasuryBalanceSummary()));
        } catch (DaoException e) {
            AllAlerts.handleError(text("treasury.error.load.title"), e);
        }
    }

    @FXML
    private void newTreasury() {
        selectedTreasury = null;
        nameField.clear();
        amountField.clear();
        typeCombo.getSelectionModel().select(TreasuryType.CASH);
        activeCheck.setSelected(true);
        feeField.clear();
        accountField.clear();
        minimumField.clear();
        sortField.clear();
        selectCurrency(null);
        treasuryTable.getSelectionModel().clearSelection();
    }

    @FXML
    private void saveTreasury() {
        try {
            Treasury treasury = new Treasury();
            readForm(treasury);
            validateTreasury(treasury);

            treasuryService.insert(treasury);
            afterWrite(text("treasury.msg.save.success"));
            newTreasury();
        } catch (Exception e) {
            AllAlerts.handleError(text("treasury.op.save"), e);
        }
    }

    @FXML
    private void updateTreasury() {
        try {
            if (selectedTreasury == null) {
                AllAlerts.alertError(text("treasury.msg.select.to.edit"));
                return;
            }

            readForm(selectedTreasury);
            validateTreasury(selectedTreasury);

            treasuryService.update(selectedTreasury);
            afterWrite(text("treasury.msg.update.success"));
        } catch (Exception e) {
            AllAlerts.handleError(text("treasury.op.update"), e);
        }
    }

    /** What the wallets kept, by treasury and by kind - the fee percentages are set on this screen. */
    @FXML
    private void openFeeReport() {
        try {
            new com.hamza.account.view.OpenApplication<>(new WalletFeeReportController());
        } catch (Exception e) {
            AllAlerts.handleError(text("treasury.fee.report.title"), e);
        }
    }

    @FXML
    private void printTreasuries() {
        List<TreasuryBalanceSummary> rows = List.copyOf(treasuryTable.getItems());
        if (rows.isEmpty()) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        String title = text("treasury.screen.title");
        File target = TablePdfReport.chooseTarget(treasuryTable.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        TablePdfReport.write(target, title, "",
                TablePdfLayout.from(treasuryTable, rows, Set.of()), () -> { });
    }

    private void afterWrite(String message) {
        loadTreasuries();
        if (eventBus != null) {
            eventBus.publish(new TreasuriesChanged());
        }
        AllAlerts.alertSaveWithMessage(message);
    }

    private void readForm(Treasury treasury) throws UserValidationException {
        treasury.setName(nameField.getText() == null ? "" : nameField.getText().trim());
        // In a foreign currency the box holds the opening in that currency, and the service values it
        // in the base at the opening day's rate (V81); in the base it is the opening itself.
        BigDecimal opening = parseAmount(amountField.getText());
        Currency currency = currencyCombo.getValue();
        treasury.setCurrencyId(isForeign() ? currency.id() : null);
        treasury.setOpeningForeign(isForeign() ? opening : null);
        treasury.setAmount(isForeign() ? BigDecimal.ZERO : opening);
        treasury.setType(typeCombo.getValue());
        treasury.setActive(activeCheck.isSelected());
        treasury.setFeePercent(parseAmount(feeField.getText()));
        treasury.setAccountNumber(accountField.getText());
        treasury.setMinBalance(parseAmount(minimumField.getText()));
        treasury.setSortOrder(parseOrder(sortField.getText()));

        // Who entered the row. Falls back to the seeded admin (id 1, the DEFAULT behind
        // every user_id column) rather than failing: this screen can be reached before a
        // session exists in a test harness, and the audit trigger answers "who changed it".
        Users user = CurrentUser.getOrNull();
        treasury.setUserId(user == null ? 1 : user.getId());
    }

    /**
     * Loads the row's editable side. The table holds the balance view, which is
     * read-only and carries no {@code user_id} or {@code opening_date}, so the
     * treasury itself is fetched rather than reconstructed from the row - saving a
     * half-populated object back is how columns get silently zeroed.
     */
    private void fillForm(TreasuryBalanceSummary row) {
        if (row == null) {
            return;
        }
        try {
            selectedTreasury = treasuryService.getTreasuryById(row.id());
        } catch (DaoException e) {
            AllAlerts.handleError(text("treasury.error.load.title"), e);
            return;
        }
        if (selectedTreasury == null) {
            return;
        }

        nameField.setText(selectedTreasury.getName());
        selectCurrency(selectedTreasury.getCurrencyId());
        amountField.setText(String.valueOf(selectedTreasury.getCurrencyId() == null
                ? selectedTreasury.getAmount() : selectedTreasury.getOpeningForeign()));
        typeCombo.getSelectionModel().select(selectedTreasury.getType());
        activeCheck.setSelected(selectedTreasury.isActive());
        feeField.setText(String.valueOf(selectedTreasury.getFeePercent()));
        accountField.setText(selectedTreasury.getAccountNumber() == null ? "" : selectedTreasury.getAccountNumber());
        minimumField.setText(String.valueOf(selectedTreasury.getMinBalance()));
        sortField.setText(String.valueOf(selectedTreasury.getSortOrder()));
    }

    /** Blank is zero; anything else has to be a whole number that is not negative. */
    private int parseOrder(String value) throws UserValidationException {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            int order = Integer.parseInt(value.trim());
            if (order < 0) {
                throw new UserValidationException(text("treasury.error.sort.order"));
            }
            return order;
        } catch (NumberFormatException e) {
            throw new UserValidationException(text("treasury.error.sort.order"), e);
        }
    }

    private BigDecimal parseAmount(String value) throws UserValidationException {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new UserValidationException(text("treasury.error.invalid.balance"), e);
        }
    }

    private void validateTreasury(Treasury treasury) throws UserValidationException {
        if (treasury.getName() == null || treasury.getName().isBlank()) {
            throw new UserValidationException(text("treasury.error.name.required"));
        }
        if (treasury.getType() == null) {
            throw new UserValidationException(text("treasury.error.type.required"));
        }
        if (treasury.getAmount() == null || treasury.getAmount().compareTo(BigDecimal.ZERO) < 0
                || (treasury.getOpeningForeign() != null && treasury.getOpeningForeign().signum() < 0)) {
            throw new UserValidationException(text("treasury.error.balance.negative"));
        }
        BigDecimal fee = treasury.getFeePercent();
        if (fee == null || fee.signum() < 0 || fee.compareTo(new BigDecimal("100")) > 0) {
            throw new UserValidationException(text("treasury.error.fee.range"));
        }
    }

    @FXML
    private void toggleTreasuryForm() {
        updateTreasuryFormToggle(!treasuryForm.isVisible());
    }

    private void updateTreasuryFormToggle(boolean expanded) {
        treasuryForm.setVisible(expanded);
        treasuryForm.setManaged(expanded);
        formToggleButton.setText(text(expanded ? "treasury.form.hide" : "treasury.form.show"));
    }

    private String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }

    private String text(String key, Object... args) {
        return LanguageManager.getInstance().getString(key, args);
    }
}
