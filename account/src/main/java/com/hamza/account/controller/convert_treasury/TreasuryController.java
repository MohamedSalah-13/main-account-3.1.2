package com.hamza.account.controller.convert_treasury;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.TreasuriesChanged;
import com.hamza.account.features.events.TreasuryMovementRecorded;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.model.domain.Users;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.TreasuryBalanceService;
import com.hamza.account.service.TreasuryService;
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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.util.StringConverter;

import java.math.BigDecimal;

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

    @FXML
    private TableView<TreasuryBalanceSummary> treasuryTable;

    @FXML
    private BorderPane root;

    private final TreasuryService treasuryService;
    private final TreasuryBalanceService balanceService;
    private final EventBus eventBus;
    private final Subscriptions subscriptions = new Subscriptions();

    private Treasury selectedTreasury;

    public TreasuryController(DaoFactory daoFactory) {
        this.treasuryService = new TreasuryService(daoFactory);
        this.balanceService = new TreasuryBalanceService(daoFactory);
        this.eventBus = ServiceRegistry.get(EventBus.class);
    }

    @FXML
    private void initialize() {
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
                Columns.number("treasury.column.opening", TreasuryBalanceSummary::opening),
                Columns.number("treasury.column.in", TreasuryBalanceSummary::totalIn),
                Columns.number("treasury.column.out", TreasuryBalanceSummary::totalOut),
                Columns.number("treasury.column.balance", TreasuryBalanceSummary::balance),
                Columns.number("treasury.column.fee", TreasuryBalanceSummary::feePercent));
    }

    @FXML
    private void loadTreasuries() {
        try {
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

    private void afterWrite(String message) {
        loadTreasuries();
        if (eventBus != null) {
            eventBus.publish(new TreasuriesChanged());
        }
        AllAlerts.alertSaveWithMessage(message);
    }

    private void readForm(Treasury treasury) throws UserValidationException {
        treasury.setName(nameField.getText() == null ? "" : nameField.getText().trim());
        treasury.setAmount(parseAmount(amountField.getText()));
        treasury.setType(typeCombo.getValue());
        treasury.setActive(activeCheck.isSelected());
        treasury.setFeePercent(parseAmount(feeField.getText()));

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
        amountField.setText(String.valueOf(selectedTreasury.getAmount()));
        typeCombo.getSelectionModel().select(selectedTreasury.getType());
        activeCheck.setSelected(selectedTreasury.isActive());
        feeField.setText(String.valueOf(selectedTreasury.getFeePercent()));
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
        if (treasury.getAmount() == null || treasury.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new UserValidationException(text("treasury.error.balance.negative"));
        }
        BigDecimal fee = treasury.getFeePercent();
        if (fee == null || fee.signum() < 0 || fee.compareTo(new BigDecimal("100")) > 0) {
            throw new UserValidationException(text("treasury.error.fee.range"));
        }
    }

    private String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
