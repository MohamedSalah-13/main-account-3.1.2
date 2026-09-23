package com.hamza.account.controller.convert_treasury;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.CurrencyFormat;
import com.hamza.account.features.currency.CurrencyService;
import com.hamza.account.features.events.TreasuryMovementRecorded;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.features.treasury.CashCategory;
import com.hamza.account.features.treasury.CashDirection;
import com.hamza.account.features.treasury.CashMovement;
import com.hamza.account.features.treasury.CashMovementCommand;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.features.treasury.TreasuryCashService;
import com.hamza.account.features.treasury.TreasuryHistoryFilter;
import com.hamza.account.features.treasury.TreasuryHistoryPage;
import com.hamza.account.features.treasury.TreasuryVoucherLayout;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.TreasuryBalanceService;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * Puts cash into a treasury and takes it out.
 * <p>
 * {@code treasury_deposit_expenses} was read in two places and written in none:
 * {@code treasury_balance} sums it, and the shift report has always shown "total
 * deposits" over rows nobody could create. This is the screen that creates them.
 * <p>
 * A withdrawal here is <b>not</b> an expense. An expense says what the money was
 * spent on and reduces the profit; this only says it left the drawer, and the profit
 * is untouched - which is also why the owner's own drawings will land here rather
 * than in {@code expenses_details} (docs/treasury-plan.md §4).
 */
@FxmlPath(pathFile = "treasury/treasuryCash.fxml")
public class TreasuryCashController {

    private static final String AMOUNT_COLUMN = "cashAmount";


    @FXML
    private BorderPane root;

    @FXML
    private ComboBox<TreasuryBalanceSummary> treasuryCombo;

    @FXML
    private ComboBox<CashDirection> directionCombo;

    @FXML
    private ComboBox<CashCategory> categoryCombo;

    @FXML
    private TextField amountField;

    @FXML
    private TextField statementField;

    @FXML
    private TextField descriptionField;

    @FXML
    private DatePicker datePicker;

    @FXML
    private Label availableLabel;

    @FXML
    private Button saveButton;

    @FXML
    private TableView<CashMovement> movementsTable;

    @FXML
    private HBox historyBar;

    @FXML
    private HBox historyFooter;

    private TreasuryHistoryBar history;
    private TreasuryHistoryTable<CashMovement> historyTable;

    /** Every currency by its code, read with the treasuries - so a history cell is not a query. */
    private java.util.Map<String, Currency> currenciesByCode = java.util.Map.of();

    private final TreasuryCashService cashService;
    private final TreasuryBalanceService balanceService;
    private final EventBus eventBus;

    public TreasuryCashController(DaoFactory daoFactory) {
        this.cashService = new TreasuryCashService(daoFactory);
        this.balanceService = new TreasuryBalanceService(daoFactory);
        this.eventBus = ServiceRegistry.get(EventBus.class);
    }

    @FXML
    private void initialize() {
        datePicker.setValue(LocalDate.now());

        directionCombo.setItems(FXCollections.observableArrayList(CashDirection.values()));
        directionCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(CashDirection direction) {
                return direction == null ? "" : text(direction.labelKey());
            }

            @Override
            public CashDirection fromString(String value) {
                return directionCombo.getValue();
            }
        });
        directionCombo.getSelectionModel().select(CashDirection.DEPOSIT);

        categoryCombo.setItems(FXCollections.observableArrayList(CashCategory.values()));
        categoryCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(CashCategory category) {
                return category == null ? "" : text(category.labelKey());
            }

            @Override
            public CashCategory fromString(String value) {
                return categoryCombo.getValue();
            }
        });
        categoryCombo.getSelectionModel().select(CashCategory.NORMAL);
        // Capital only ever comes in and drawings only ever go out, so the category
        // chooses the direction rather than letting the two contradict each other. The
        // service refuses the impossible pair anyway, and so does a CHECK in V21 - this
        // is only so the user never has to be told about a rule they could not have met.
        categoryCombo.getSelectionModel().selectedItemProperty().addListener((obs, was, now) -> {
            boolean fixed = now != null && now.requires() != null;
            if (fixed) {
                directionCombo.getSelectionModel().select(now.requires());
            }
            directionCombo.setDisable(fixed);
        });

        historyTable = new TreasuryHistoryTable<>(movementsTable, "treasuryCashMovementsTable", List.of(
                TreasuryHistoryTable.withId("cashDate",
                        Columns.date("treasury.cash.column.date", CashMovement::date)),
                TreasuryHistoryTable.withId("cashTreasury",
                        Columns.text("treasury.cash.column.treasury", CashMovement::treasuryName)),
                TreasuryHistoryTable.withId("cashDirection", Columns.text("treasury.cash.column.direction",
                        movement -> text(movement.direction().labelKey()))),
                TreasuryHistoryTable.withId("cashCategory", Columns.text("treasury.cash.column.category",
                        movement -> text(movement.category().labelKey()))),
                TreasuryHistoryTable.withId(AMOUNT_COLUMN,
                        Columns.money("treasury.cash.column.amount", CashMovement::amount)),
                // A movement on a treasury in a foreign currency (V81): what moved in it, and the rate
                // copied onto the row. The amount beside it is its value in the base.
                TreasuryHistoryTable.withId("cashForeign",
                        Columns.text("treasury.cash.column.foreign", this::foreignText)),
                TreasuryHistoryTable.withId("cashStatement",
                        Columns.text("treasury.cash.column.statement", CashMovement::statement))),
                AppPermissions.TREASURY_DEPOSIT, this::deleteMovement, this::printVoucher);
        history = new TreasuryHistoryBar(true, this::loadHistory, this::printHistory, this::exportHistory);
        history.installIn(historyBar, historyFooter);

        treasuryCombo.getSelectionModel().selectedItemProperty().addListener(
                (obs, was, now) -> showAvailable());
        // The form's order as it reads, down to the save button. The direction is out of it while a
        // capital category fixes it, and Enter passes over it then.
        whenEnterPressed(treasuryCombo, directionCombo, amountField, datePicker, statementField,
                descriptionField, categoryCombo, saveButton);
        TreasuryCombo.onEnter(treasuryCombo, directionCombo, amountField);
        // A foreign treasury's movement is valued at the rate of its own day, so the line follows the date.
        datePicker.valueProperty().addListener((obs, was, now) -> showAvailable());

        reload();
    }

    @FXML
    private void reload() {
        try {
            TreasuryCombo.fill(treasuryCombo, balanceService.getActiveTreasuryBalances());
            currenciesByCode = TreasuryCombo.currencies().values().stream()
                    .collect(java.util.stream.Collectors.toMap(Currency::code, currency -> currency));
            showAvailable();
            // Every treasury, closed ones included: an old deposit is found under the till it was made on.
            history.setTreasuries(balanceService.getTreasuryBalanceSummary());
            history.load();
        } catch (DaoException e) {
            AllAlerts.handleError(text("treasury.error.load.title"), e);
        }
    }

    @FXML
    private void saveMovement() {
        CashDirection direction = directionCombo.getValue();
        try {
            TreasuryBalanceSummary treasury = treasuryCombo.getValue();
            if (treasury == null || direction == null) {
                throw new UserValidationException(text("treasury.cash.error.select"));
            }

            BigDecimal amount = TreasuryCombo.amount(amountField.getText(),
                    "treasury.cash.error.amount");

            cashService.record(new CashMovementCommand(
                    treasury.id(), direction, categoryCombo.getValue(), amount, datePicker.getValue(),
                    statementField.getText() == null ? "" : statementField.getText().trim(),
                    descriptionField.getText() == null ? "" : descriptionField.getText().trim(),
                    userId()));

            publish(treasury.id());
            amountField.clear();
            statementField.clear();
            descriptionField.clear();
            reload();
            AllAlerts.alertSaveWithMessage(text("treasury.cash.msg.success"));
        } catch (Exception e) {
            AllAlerts.handleError(text(direction == null
                    ? "treasury.cash.error.deposit"
                    : direction.failureKey()), e);
        }
    }

    /**
     * What the picked treasury holds, and - for one in a foreign currency - the rate the movement will be
     * valued at on the chosen day, or that there is none: the service refuses then, and saying so before
     * the save spares the person typing a movement nobody can record (V81, docs/currency-plan.md §11).
     */
    private void showAvailable() {
        String available = TreasuryCombo.availableText(treasuryCombo);
        Currency currency = TreasuryCombo.currencyOf(treasuryCombo, treasuryCombo.getValue());
        amountField.setPromptText(currency == null ? "" : currency.code());
        CurrencyService currencies = ServiceRegistry.get(CurrencyService.class);
        if (currency == null || currencies == null || datePicker.getValue() == null) {
            availableLabel.setText(available);
            return;
        }
        try {
            String rate = currencies.rateOn(currency.id(), datePicker.getValue())
                    .map(inForce -> LanguageManager.getInstance().getString("treasury.cash.rate.day",
                            currency.code(), CurrencyFormat.rate(inForce.rate())))
                    .orElse(text("treasury.cash.rate.none"));
            availableLabel.setText(available + "  |  " + rate);
        } catch (DaoException e) {
            availableLabel.setText(available);
        }
    }

    /** "50.00 USD @ 48.5" - the amount to its currency's places, and the rate copied onto the row. */
    private String foreignText(CashMovement movement) {
        if (!movement.isForeign()) {
            return "";
        }
        Currency currency = currenciesByCode.get(movement.currencyCode());
        String amount = currency == null ? Columns.quantity(movement.foreignAmount())
                : CurrencyFormat.amount(movement.foreignAmount(), currency);
        return amount + " " + movement.currencyCode() + " @ " + CurrencyFormat.rate(movement.exchangeRate());
    }

    /** The row's own button: there is no "choose a movement first" to get wrong. */
    private void deleteMovement(CashMovement selected) {
        if (!AllAlerts.confirmDelete()) {
            return;
        }
        try {
            var reason = com.hamza.account.controller.users.ShiftCorrectionReasonPrompt.forDelete();
            if (reason.isEmpty()) return;
            cashService.delete(selected.id(), reason.get());
            publish(selected.treasuryId());
            reload();
            AllAlerts.alertDelete();
        } catch (Exception e) {
            AllAlerts.handleError(text("treasury.cash.op.delete"), e);
        }
    }

    private void printVoucher(CashMovement row) {
        TreasuryVoucherPrinter.print(movementsTable, row.id(), () -> TreasuryVoucherLayout.of(
                cashService.forVoucher(row.id()), TreasuryVoucherPrinter.letterhead(),
                LanguageManager.getInstance()::getString, TreasuryVoucherPrinter.now()));
    }

    private void loadHistory(TreasuryHistoryFilter filter) {
        try {
            TreasuryHistoryPage<CashMovement> page = cashService.history(filter);
            historyTable.show(page.rows());
            history.showPage(page, LanguageManager.getInstance().getString("treasury.cash.totals",
                    page.totals().count(), Columns.money(page.totals().first()),
                    Columns.money(page.totals().second())));
        } catch (DaoException e) {
            AllAlerts.handleError(text("treasury.error.load.title"), e);
        }
    }

    /**
     * No totals line on paper: deposits and withdrawals share one amount column, and a sum of the
     * two is a number that means nothing. The period's two totals are in the subtitle instead.
     */
    private void printHistory() {
        TreasuryHistoryFilter filter = history.filter();
        if (filter == null) return;
        try {
            TreasuryHistoryPage<CashMovement> extract = cashService.forPrint(filter);
            if (extract.truncated()) {
                AllAlerts.alertError(text("treasury.statement.error.print.limit"));
                return;
            }
            String totals = LanguageManager.getInstance().getString("treasury.cash.totals",
                    extract.totals().count(), Columns.money(extract.totals().first()),
                    Columns.money(extract.totals().second()));
            historyTable.print(text("treasury.cash.report.title"), history.periodText() + "  |  " + totals,
                    extract.rows(), Set.of());
        } catch (Exception e) {
            AllAlerts.handleError(text("treasury.statement.operation.print"), e);
        }
    }

    private void exportHistory() {
        TreasuryHistoryFilter filter = history.filter();
        if (filter == null) return;
        try {
            historyTable.exportExcel(text("treasury.cash.report.title"), cashService.forPrint(filter).rows());
        } catch (Exception e) {
            AllAlerts.handleError(text("treasury.history.export.excel"), e);
        }
    }

    private void publish(int treasuryId) {
        if (eventBus != null) {
            eventBus.publish(new TreasuryMovementRecorded(treasuryId));
        }
    }

    /** No session is a refusal, not user 1: a deposit filed under the administrator is one nobody made. */
    private int userId() {
        return CurrentUser.get().getId();
    }

    private String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
