package com.hamza.account.controller.convert_treasury;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.CurrencyFormat;
import com.hamza.account.features.treasury.TreasuryExchange;
import com.hamza.account.features.events.TreasuryMovementRecorded;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.features.treasury.TreasuryHistoryFilter;
import com.hamza.account.features.treasury.TreasuryHistoryPage;
import com.hamza.account.features.treasury.TreasuryTransfer;
import com.hamza.account.features.treasury.TreasuryTransferCommand;
import com.hamza.account.features.treasury.TreasuryTransferService;
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
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

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
    private VBox transferForm;

    @FXML
    private Button formToggleButton;

    @FXML
    private ComboBox<TreasuryBalanceSummary> fromCombo;

    @FXML
    private ComboBox<TreasuryBalanceSummary> toCombo;

    @FXML
    private TextField amountField;

    /** Between two currencies (V81): what reached the destination, in its own currency. */
    @FXML
    private TextField receivedField;

    @FXML
    private Label receivedCaption;

    /** The rate the two amounts imply - shown, never stored: it is their quotient. */
    @FXML
    private Label exchangeRateLabel;

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
    private Button saveButton;

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
        updateTransferFormToggle(true);
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
                // An exchange (V81): what left and what arrived, each in its own treasury's currency.
                // The amount beside them is the one figure the books moved, in the base.
                TreasuryHistoryTable.withId("transferExchange",
                        Columns.text("treasury.transfer.column.exchange", this::exchangeText)),
                TreasuryHistoryTable.withId(FEE_COLUMN,
                        Columns.money("treasury.transfer.column.fee", TreasuryTransfer::fee)),
                TreasuryHistoryTable.withId("transferNotes",
                        Columns.text("treasury.transfer.column.notes", TreasuryTransfer::notes))),
                AppPermissions.TREASURY_TRANSFER, this::deleteTransfer, this::printVoucher);
        history = new TreasuryHistoryBar(false, this::loadHistory, this::printHistory, this::exportHistory);
        history.installIn(historyBar, historyFooter);

        fromCombo.getSelectionModel().selectedItemProperty().addListener((obs, was, now) -> showCurrencies());
        toCombo.getSelectionModel().selectedItemProperty().addListener((obs, was, now) -> showCurrencies());
        // The form's order, down to the save button - a scanner or a typist ends a field with Enter. Two
        // of those fields can be out of the form: what was received between one currency, and the fee
        // from a treasury in a foreign one; Enter passes over whichever is missing.
        whenEnterPressed(fromCombo, toCombo, amountField, receivedField, datePicker, feeField, notesField,
                saveButton);
        TreasuryCombo.onEnter(amountField, receivedField, datePicker);
        TreasuryCombo.onEnter(datePicker, feeField, notesField);
        amountField.textProperty().addListener((obs, was, now) -> showImpliedRate());
        receivedField.textProperty().addListener((obs, was, now) -> showImpliedRate());

        reload();
    }

    @FXML
    private void toggleTransferForm() {
        updateTransferFormToggle(!transferForm.isVisible());
    }

    private void updateTransferFormToggle(boolean expanded) {
        transferForm.setVisible(expanded);
        transferForm.setManaged(expanded);
        formToggleButton.setText(text(expanded ? "treasury.form.hide" : "treasury.form.show"));
    }

    @FXML
    private void reload() {
        try {
            List<TreasuryBalanceSummary> treasuries = balanceService.getActiveTreasuryBalances();
            TreasuryCombo.fill(fromCombo, treasuries);
            TreasuryCombo.fill(toCombo, treasuries);
            currenciesByCode = TreasuryCombo.currencies().values().stream()
                    .collect(java.util.stream.Collectors.toMap(Currency::code, currency -> currency));
            showCurrencies();
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
            // Read only between two currencies; the service asks for it there and nowhere else.
            BigDecimal received = isExchange() ? TreasuryCombo.optionalAmount(receivedField.getText(),
                    "treasury.transfer.error.amount") : null;

            transferService.transfer(new TreasuryTransferCommand(
                    from.id(), to.id(), amount, datePicker.getValue(),
                    notesField.getText() == null ? "" : notesField.getText().trim(),
                    userId(), fee, received));

            // Two treasuries moved, so the event is published for each of them: a screen
            // watching one side must not have to know it was the other half of a transfer.
            publish(from.id());
            publish(to.id());

            amountField.clear();
            receivedField.clear();
            feeField.clear();
            notesField.clear();
            reload();
            AllAlerts.alertSaveWithMessage(text("treasury.transfer.msg.success"));
        } catch (Exception e) {
            AllAlerts.handleError(text("treasury.transfer.op.save"), e);
        }
    }

    /** Every currency by its code, read with the treasuries - so a history cell is not a query. */
    private java.util.Map<String, Currency> currenciesByCode = java.util.Map.of();

    /**
     * The form follows the two treasuries' currencies (V81, docs/currency-plan.md §11): between two
     * currencies it asks what was received as well and shows the rate the two amounts imply, each box
     * prompting with its currency's code; a fee is not taken from a treasury in a foreign currency.
     */
    private void showCurrencies() {
        availableLabel.setText(TreasuryCombo.availableText(fromCombo));
        Currency from = TreasuryCombo.currencyOf(fromCombo, fromCombo.getValue());
        Currency to = TreasuryCombo.currencyOf(toCombo, toCombo.getValue());
        boolean exchange = isExchange();
        for (var node : List.<javafx.scene.Node>of(receivedCaption, receivedField, exchangeRateLabel)) {
            node.setVisible(exchange);
            node.setManaged(exchange);
        }
        amountField.setPromptText(from == null ? "" : from.code());
        receivedField.setPromptText(to == null ? baseCode() : to.code());
        boolean foreignSource = from != null;
        feeField.setDisable(foreignSource);
        if (foreignSource) {
            feeField.clear();
        }
        showImpliedRate();
    }

    /** Two treasuries in two different currencies - the base being one of them or not. */
    private boolean isExchange() {
        Currency from = TreasuryCombo.currencyOf(fromCombo, fromCombo.getValue());
        Currency to = TreasuryCombo.currencyOf(toCombo, toCombo.getValue());
        if (from == null && to == null) {
            return false;
        }
        return from == null || to == null || from.id() != to.id();
    }

    /**
     * "1 USD = 48.5 EGP" while both amounts are typed: the rate is always that of the side that is not
     * the base, in the base - or, between two foreign currencies, the destination's per unit of the source.
     */
    private void showImpliedRate() {
        if (!isExchange()) {
            exchangeRateLabel.setText("");
            return;
        }
        BigDecimal sent = parsed(amountField.getText());
        BigDecimal received = parsed(receivedField.getText());
        Currency from = TreasuryCombo.currencyOf(fromCombo, fromCombo.getValue());
        Currency to = TreasuryCombo.currencyOf(toCombo, toCombo.getValue());
        String unit;
        String per;
        BigDecimal rate;
        if (from == null) {
            unit = to.code();
            per = baseCode();
            rate = TreasuryExchange.impliedRate(sent, received);
        } else {
            unit = from.code();
            per = to == null ? baseCode() : to.code();
            rate = TreasuryExchange.impliedRate(received, sent);
        }
        // The formula is one left-to-right piece: in the Arabic layout its leading "1" would otherwise
        // stay with the Arabic words and the rest drift to the far end - "USD = 48.5 EGP 1" (rendered
        // 2026-09-23). The mark in front is a strong left-to-right character that carries the "1" with it.
        exchangeRateLabel.setText(rate == null ? ""
                : LanguageManager.getInstance().getString("treasury.exchange.rate.line",
                        "\u200E1 " + unit + " = " + CurrencyFormat.indicative(rate) + " " + per));
    }

    private static BigDecimal parsed(String text) {
        try {
            return text == null || text.isBlank() ? null : new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String baseCode() {
        return currenciesByCode.values().stream().filter(Currency::base).map(Currency::code)
                .findFirst().orElse("");
    }

    /** "100.00 USD → 4,850.00" - each side in its own currency; blank for a transfer in the base. */
    private String exchangeText(TreasuryTransfer transfer) {
        if (!transfer.isForeign()) {
            return "";
        }
        String base = baseCode();
        return side(transfer.sent(), transfer.currencyFrom() == null ? base : transfer.currencyFrom())
                + " \u2192 " + side(transfer.received(), transfer.currencyTo() == null ? base : transfer.currencyTo());
    }

    private String side(BigDecimal amount, String code) {
        Currency currency = currenciesByCode.get(code);
        return (currency == null ? Columns.quantity(amount) : CurrencyFormat.amount(amount, currency)) + " " + code;
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

    private void printVoucher(TreasuryTransfer row) {
        TreasuryVoucherPrinter.print(transfersTable, row.id(), () -> TreasuryVoucherLayout.of(
                transferService.forVoucher(row.id()), TreasuryVoucherPrinter.letterhead(),
                LanguageManager.getInstance()::getString, TreasuryVoucherPrinter.now()));
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
