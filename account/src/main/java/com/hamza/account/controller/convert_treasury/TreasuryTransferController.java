package com.hamza.account.controller.convert_treasury;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.TreasuryMovementRecorded;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.features.treasury.TreasuryTransfer;
import com.hamza.account.features.treasury.TreasuryTransferCommand;
import com.hamza.account.features.treasury.TreasuryTransferService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Moves money from one treasury to another.
 * <p>
 * The screen for a table that has existed since the baseline with nothing able to
 * write to it - so a shop with a cash drawer and a Vodafone Cash wallet could record
 * every sale against either and never record moving money between them, and the two
 * balances drifted apart with no way to correct them but a fake deposit.
 * <p>
 * Every rule lives in {@link TreasuryTransferService}. What is here is a convenience
 * and not a check: the available balance is shown beside the source so the refusal
 * is rare, but the refusal is what makes it true.
 */
@FxmlPath(pathFile = "treasury/treasuryTransfer.fxml")
public class TreasuryTransferController {

    private static final int RECENT_LIMIT = 50;

    @FXML
    private BorderPane root;

    @FXML
    private ComboBox<TreasuryBalanceSummary> fromCombo;

    @FXML
    private ComboBox<TreasuryBalanceSummary> toCombo;

    @FXML
    private TextField amountField;

    @FXML
    private TextField notesField;

    @FXML
    private DatePicker datePicker;

    @FXML
    private Label availableLabel;

    @FXML
    private TableView<TreasuryTransfer> transfersTable;

    private final TreasuryTransferService transferService;
    private final TreasuryBalanceService balanceService;
    private final EventBus eventBus;

    public TreasuryTransferController(DaoFactory daoFactory) {
        this.transferService = new TreasuryTransferService(daoFactory);
        this.balanceService = new TreasuryBalanceService(daoFactory);
        this.eventBus = ServiceRegistry.get(EventBus.class);
    }

    @FXML
    private void initialize() {
        datePicker.setValue(LocalDate.now());

        transfersTable.getColumns().setAll(
                Columns.text("treasury.transfer.column.from", TreasuryTransfer::fromTreasuryName),
                Columns.text("treasury.transfer.column.to", TreasuryTransfer::toTreasuryName),
                Columns.number("treasury.transfer.column.amount", TreasuryTransfer::amount),
                Columns.date("treasury.transfer.column.date", TreasuryTransfer::transferDate),
                Columns.text("treasury.transfer.column.notes", TreasuryTransfer::notes));

        fromCombo.getSelectionModel().selectedItemProperty().addListener(
                (obs, was, now) -> availableLabel.setText(TreasuryCombo.availableText(now)));

        reload();
    }

    @FXML
    private void reload() {
        try {
            List<TreasuryBalanceSummary> treasuries = balanceService.getActiveTreasuryBalances();
            TreasuryCombo.fill(fromCombo, treasuries);
            TreasuryCombo.fill(toCombo, treasuries);
            availableLabel.setText(TreasuryCombo.availableText(fromCombo.getValue()));

            transfersTable.setItems(FXCollections.observableArrayList(
                    transferService.recent(RECENT_LIMIT)));
        } catch (DaoException e) {
            AllAlerts.handleError(text("treasury.error.load.title"), e);
        }
    }

    @FXML
    private void saveTransfer() {
        try {
            TreasuryBalanceSummary from = fromCombo.getValue();
            TreasuryBalanceSummary to = toCombo.getValue();
            if (from == null || to == null) {
                throw new UserValidationException(text("treasury.transfer.error.select"));
            }

            BigDecimal amount = TreasuryCombo.amount(amountField.getText(),
                    "treasury.transfer.error.amount");

            transferService.transfer(new TreasuryTransferCommand(
                    from.id(), to.id(), amount, datePicker.getValue(),
                    notesField.getText() == null ? "" : notesField.getText().trim(),
                    userId()));

            // Two treasuries moved, so the event is published for each of them: a screen
            // watching one side must not have to know it was the other half of a transfer.
            publish(from.id());
            publish(to.id());

            amountField.clear();
            notesField.clear();
            reload();
            AllAlerts.alertSaveWithMessage(text("treasury.transfer.msg.success"));
        } catch (Exception e) {
            AllAlerts.handleError(text("treasury.transfer.op.save"), e);
        }
    }

    @FXML
    private void deleteTransfer() {
        TreasuryTransfer selected = transfersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AllAlerts.alertError(text("treasury.transfer.msg.select.to.delete"));
            return;
        }
        if (!AllAlerts.confirmDelete()) {
            return;
        }
        try {
            var reason = com.hamza.account.controller.users.ShiftCorrectionReasonPrompt.forDelete();
            if (reason.isEmpty()) return;
            transferService.delete(selected.id(), reason.get());
            publish(selected.fromTreasuryId());
            publish(selected.toTreasuryId());
            reload();
            AllAlerts.alertDelete();
        } catch (Exception e) {
            AllAlerts.handleError(text("treasury.transfer.op.delete"), e);
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
