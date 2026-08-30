package com.hamza.account.controller.convert_treasury;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.TreasuryMovementRecorded;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.features.treasury.CashCategory;
import com.hamza.account.features.treasury.CashDirection;
import com.hamza.account.features.treasury.CashMovement;
import com.hamza.account.features.treasury.CashMovementCommand;
import com.hamza.account.features.treasury.TreasuryCashService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

    private static final int RECENT_LIMIT = 50;

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
    private TableView<CashMovement> movementsTable;

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

        movementsTable.getColumns().setAll(
                Columns.text("treasury.cash.column.treasury", CashMovement::treasuryName),
                Columns.text("treasury.cash.column.direction",
                        movement -> text(movement.direction().labelKey())),
                Columns.text("treasury.cash.column.category",
                        movement -> text(movement.category().labelKey())),
                Columns.number("treasury.cash.column.amount", CashMovement::amount),
                Columns.date("treasury.cash.column.date", CashMovement::date),
                Columns.text("treasury.cash.column.statement", CashMovement::statement));

        treasuryCombo.getSelectionModel().selectedItemProperty().addListener(
                (obs, was, now) -> availableLabel.setText(TreasuryCombo.availableText(now)));

        reload();
    }

    @FXML
    private void reload() {
        try {
            TreasuryCombo.fill(treasuryCombo, balanceService.getActiveTreasuryBalances());
            availableLabel.setText(TreasuryCombo.availableText(treasuryCombo.getValue()));
            movementsTable.setItems(FXCollections.observableArrayList(
                    cashService.recent(RECENT_LIMIT)));
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

    @FXML
    private void deleteMovement() {
        CashMovement selected = movementsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AllAlerts.alertError(text("treasury.cash.msg.select.to.delete"));
            return;
        }
        if (!AllAlerts.confirmDelete()) {
            return;
        }
        try {
            cashService.delete(selected.id());
            publish(selected.treasuryId());
            reload();
            AllAlerts.alertDelete();
        } catch (Exception e) {
            AllAlerts.handleError(text("treasury.cash.op.delete"), e);
        }
    }

    private void publish(int treasuryId) {
        if (eventBus != null) {
            eventBus.publish(new TreasuryMovementRecorded(treasuryId));
        }
    }

    private int userId() {
        Users user = CurrentUser.getOrNull();
        return user == null ? 1 : user.getId();
    }

    private String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
