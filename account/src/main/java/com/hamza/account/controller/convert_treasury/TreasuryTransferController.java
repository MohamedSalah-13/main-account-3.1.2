package com.hamza.account.controller.convert_treasury;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.TreasuryMovementRecorded;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.features.treasury.TreasuryHistoryFilter;
import com.hamza.account.features.treasury.TreasuryHistoryPage;
import com.hamza.account.features.treasury.TreasuryTransfer;
import com.hamza.account.features.treasury.TreasuryTransferCommand;
import com.hamza.account.features.treasury.TreasuryTransferService;
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
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

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

    private static final String AMOUNT_COLUMN = "transferAmount";
    private static final String FEE_COLUMN = "transferFee";

    @FXML
    private BorderPane root;

    @FXML
    private ComboBox<TreasuryBalanceSummary> fromCombo;

    @FXML
    private ComboBox<TreasuryBalanceSummary> toCombo;

    @FXML
    private TextField amountField;

    /** Optional: blank means the transfer cost nothing, which between two drawers it does not. */
    @FXML
    private TextField feeField;

    @FXML
    private TextField notesField;

    @FXML
    private DatePicker datePicker;

    @FXML
    private Label availableLabel;

    @FXML
    private TableView<TreasuryTransfer> transfersTable;

    @FXML
    private HBox historyBar;

    @FXML
    private HBox historyFooter;

    private TreasuryHistoryBar history;
    private TreasuryHistoryTable<TreasuryTransfer> historyTable;

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

        historyTable = new TreasuryHistoryTable<>(transfersTable, "treasuryTransfersTable", List.of(
                TreasuryHistoryTable.withId("transferDate",
                        Columns.date("treasury.transfer.column.date", TreasuryTransfer::transferDate)),
                TreasuryHistoryTable.withId("transferFrom",
                        Columns.text("treasury.transfer.column.from", TreasuryTransfer::fromTreasuryName)),
                TreasuryHistoryTable.withId("transferTo",
                        Columns.text("treasury.transfer.column.to", TreasuryTransfer::toTreasuryName)),
                TreasuryHistoryTable.withId(AMOUNT_COLUMN,
                        Columns.money("treasury.transfer.column.amount", TreasuryTransfer::amount)),
                TreasuryHistoryTable.withId(FEE_COLUMN,
                        Columns.money("treasury.transfer.column.fee", TreasuryTransfer::fee)),
                TreasuryHistoryTable.withId("transferNotes",
                        Columns.text("treasury.transfer.column.notes", TreasuryTransfer::notes))),
                AppPermissions.TREASURY_TRANSFER, this::deleteTransfer);
        history = new TreasuryHistoryBar(false, this::loadHistory, this::printHistory, this::exportHistory);
        history.installIn(historyBar, historyFooter);

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
            // Every treasury, closed ones included: an old transfer is found under the till it was made on.
            history.setTreasuries(balanceService.getTreasuryBalanceSummary());
            history.load();
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
            BigDecimal fee = TreasuryCombo.optionalAmount(feeField.getText(),
                    "treasury.transfer.error.fee");

            transferService.transfer(new TreasuryTransferCommand(
                    from.id(), to.id(), amount, datePicker.getValue(),
                    notesField.getText() == null ? "" : notesField.getText().trim(),
                    userId(), fee));

            // Two treasuries moved, so the event is published for each of them: a screen
            // watching one side must not have to know it was the other half of a transfer.
            publish(from.id());
            publish(to.id());

            amountField.clear();
            feeField.clear();
            notesField.clear();
            reload();
            AllAlerts.alertSaveWithMessage(text("treasury.transfer.msg.success"));
        } catch (Exception e) {
            AllAlerts.handleError(text("treasury.transfer.op.save"), e);
        }
    }

    /** The row's own button: there is no "choose a transfer first" to get wrong. */
    private void deleteTransfer(TreasuryTransfer selected) {
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

    private void loadHistory(TreasuryHistoryFilter filter) {
        try {
            TreasuryHistoryPage<TreasuryTransfer> page = transferService.history(filter);
            historyTable.show(page.rows());
            history.showPage(page, totalsText(page));
        } catch (DaoException e) {
            AllAlerts.handleError(text("treasury.error.load.title"), e);
        }
    }

    private void printHistory() {
        TreasuryHistoryFilter filter = history.filter();
        if (filter == null) return;
        try {
            TreasuryHistoryPage<TreasuryTransfer> extract = transferService.forPrint(filter);
            if (extract.truncated()) {
                AllAlerts.alertError(text("treasury.statement.error.print.limit"));
                return;
            }
            historyTable.print(text("treasury.transfer.report.title"), history.periodText(), extract.rows(),
                    Set.of(AMOUNT_COLUMN, FEE_COLUMN));
        } catch (Exception e) {
            AllAlerts.handleError(text("treasury.statement.operation.print"), e);
        }
    }

    private void exportHistory() {
        TreasuryHistoryFilter filter = history.filter();
        if (filter == null) return;
        try {
            historyTable.exportExcel(text("treasury.transfer.report.title"),
                    transferService.forPrint(filter).rows());
        } catch (Exception e) {
            AllAlerts.handleError(text("treasury.history.export.excel"), e);
        }
    }

    private String totalsText(TreasuryHistoryPage<TreasuryTransfer> page) {
        return LanguageManager.getInstance().getString("treasury.transfer.totals", page.totals().count(),
                Columns.money(page.totals().first()), Columns.money(page.totals().second()));
    }

    private void publish(int treasuryId) {
        if (eventBus != null) {
            eventBus.publish(new TreasuryMovementRecorded(treasuryId));
        }
    }

    /** No session is a refusal, not user 1: a transfer filed under the administrator is one nobody made. */
    private int userId() {
        return CurrentUser.get().getId();
    }

    private String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
