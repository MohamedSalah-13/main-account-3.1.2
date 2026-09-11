package com.hamza.account.controller.name_account;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.party.PartyTableSpec.PartySearchScope;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.controller.search.PartySuggestionField;
import com.hamza.account.controller.users.ShiftCorrectionReasonPrompt;
import com.hamza.account.features.events.AccountChanged;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.events.TreasuryBalancesChanged;
import com.hamza.account.features.party.payment.OpenInvoice;
import com.hamza.account.features.party.payment.PartyEntryKind;
import com.hamza.account.features.party.payment.PartyPaymentAllocationService;
import com.hamza.account.features.party.statement.PartyStatementService;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.TreasuryService;
import com.hamza.account.treasury.WalletFee;
import com.hamza.account.view.NoteText;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.others.DoubleSetting;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * Recording one movement on a party's account: a collection, or a note.
 * <p>
 * <b>Four things this screen used to do are gone, and each was a defect rather than a style.</b>
 * <ul>
 *   <li><b>It numbered the movement itself.</b> {@code generateNextAccountCode} took
 *       {@code max(id) + 1} over {@code accountList()} - which reads
 *       {@code account_customer_table} entire, for every party, and computes every running
 *       balance in Java - and it did that on opening, after every save, and again through
 *       {@code getBalance} on every change of the selected party. Two tills collecting at the
 *       same moment chose the same number, and {@code account_num} is the primary key. It is
 *       {@code AUTO_INCREMENT}; the database numbers it now, and the screen says so.</li>
 *   <li><b>It read the balance off that same full scan</b>, by taking the last element of the
 *       list and hoping the sort had put the newest there. One scalar query answers it
 *       ({@link PartyStatementService#currentBalance}).</li>
 *   <li><b>It identified the party by the text of their name</b>, through a combo of every name
 *       in the database and a linear lookup back to an id. It uses
 *       {@link PartySuggestionField}, which searches in SQL and answers with the party.</li>
 *   <li><b>It could only record a collection.</b> {@code customers_accounts.purchase} had no
 *       writer, so an opening balance entered too low could never be corrected - while
 *       {@code OpeningBalanceGuard} told the user to "record a movement on the account". See
 *       {@link PartyEntryKind}.</li>
 * </ul>
 * And {@code numberInv} is written at last: a collection may be put against a particular
 * invoice, which is what an ageing report is made of.
 */
@FxmlPath(pathFile = "addAccount-view.fxml")
public class Add_AccountController<T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T3, T4> implements AddInterface {

    /** What {@code numberInv} holds for a movement that settles no particular invoice. */
    private static final OpenInvoice ON_ACCOUNT = null;

    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final TreasuryService treasuryService = ServiceRegistry.get(TreasuryService.class);
    private final PartyStatementService statementService = new PartyStatementService();
    private final PartyPaymentAllocationService allocationService = new PartyPaymentAllocationService();

    /**
     * What the party owes before this movement, as read from the ledger.
     * <p>
     * Held rather than read back out of {@code txtBalance}: that field carries a
     * {@code TextFormatter} whose converter round-trips through {@code Double}, so the string put
     * into it comes back as {@code Double.toString} - "1050.0" for a balance the screen beside it
     * writes as "1,050.00". Parsing the display to do arithmetic means doing the arithmetic on
     * whatever the display happened to survive.
     */
    private BigDecimal balanceBefore = BigDecimal.ZERO;

    /** The movement being edited, or 0 for a new one. */
    private final int movementId;
    private final String name;
    private int code_id;

    private PartySuggestionField<T3> partyField;

    @FXML
    private GridPane gridPane;
    @FXML
    private DatePicker date;
    @FXML
    private TextField txtCode, txtBalance, txtPaid, txtAmount, txtAmountInv;
    @FXML
    private TextArea txtNotes;
    @FXML
    private Label labelCode, labelName, labelDate, labelBalance, labelPaid, labelDetails,
            labelAmount, labelTreasure, labelKind, labelInvoice, lAmountInv;
    @FXML
    private ComboBox<String> comboTreasury;
    @FXML
    private ComboBox<PartyEntryKind> comboKind;
    @FXML
    private ComboBox<OpenInvoice> comboInvoice;

    /**
     * The wallet fee row. Hidden entirely for a treasury that charges nothing, which is every
     * cash drawer and most installs - a field that is always zero is a field every user learns
     * to ignore, including on the day it is not zero.
     */
    @FXML
    private Label labelFee;
    @FXML
    private TextField txtFee;

    public Add_AccountController(DaoFactory daoFactory, DataPublisher dataPublisher,
                                DataInterface<?, ?, T3, T4> dataInterface,
                                int code_id, int num, String name) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.code_id = code_id;
        this.movementId = num;
        this.name = name;
    }

    @FXML
    public void initialize() {
        otherSetting();
        addTreasurySetting();
        if (movementId > 0) {
            selectData();
        }
    }

    @Override
    public void otherSetting() {
        var lm = LanguageManager.getInstance();
        // Only the fields whose caption depends on something are set here; the rest are
        // resource keys in the FXML, which is what stops one being forgotten.
        DateSetting.dateAction(date);
        // Only the two fields somebody types in take a number formatter. The other three are
        // read-only displays, and that formatter would rewrite what they show: its converter goes
        // through Double, so MoneyMath's "1050.00" comes back "1050.0" while the balances screen
        // next door writes "1,050.00" for the same figure. One ledger, two spellings of one
        // number - which is the defect Columns.money exists to end. See moneyText below.
        setTextFormatter(txtPaid, txtFee);
        for (TextField display : new TextField[]{txtBalance, txtAmount, txtAmountInv}) {
            display.setEditable(false);
            display.setFocusTraversable(false);
            display.getStyleClass().add("app-readonly-amount");
        }

        addPartyField();
        addKindCombo();
        addInvoiceCombo();

        txtCode.setText(movementId > 0
                ? String.valueOf(movementId) : lm.getString("party.payment.code.automatic"));

        txtPaid.textProperty().addListener((observable, was, now) -> {
            recomputeRest();
            refreshWalletFee();
        });
        txtPaid.disableProperty().bind(partyField.chosenPartyProperty().isNull());
        whenEnterPressed(txtPaid, txtNotes);

        comboTreasury.getSelectionModel().selectedItemProperty()
                .addListener((observable, was, now) -> refreshWalletFee());
        showFeeRow(false);

        txtNotes.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                try {
                    new NoteText(txtNotes).start(new Stage());
                } catch (Exception e) {
                    logException(e);
                }
            }
        });
    }

    /**
     * The party picker: the same field the invoice header uses.
     * <p>
     * It answers with the party rather than with their name, so nothing here looks an id up from
     * a string - which is what {@code NameService.getCodeByName} did, over a list of every party
     * in the database, loaded for that purpose.
     */
    private void addPartyField() {
        try {
            // Everyone, stopped parties included: a debt does not stop being owed when
            // you stop selling, and this is the only screen that collects one.
            partyField = new PartySuggestionField<>(
                    nameAndAccountInterface.searchInterface(PartySearchScope.EVERYONE));
        } catch (Exception e) {
            logException(e);
            return;
        }
        gridPane.add(partyField, 1, 2);
        partyField.chosenPartyProperty().addListener((observable, was, party) -> onPartyChosen(party));
        if (code_id > 0) {
            try {
                partyField.select(nameAndAccountInterface.getNameById(code_id));
            } catch (Exception e) {
                logException(e);
            }
        } else if (name != null) {
            partyField.setText(name);
        }
    }

    private void addKindCombo() {
        comboKind.setItems(FXCollections.observableArrayList(PartyEntryKind.values()));
        comboKind.setConverter(new StringConverter<>() {
            @Override
            public String toString(PartyEntryKind kind) {
                return kind == null ? "" : LanguageManager.getInstance().getString(kind.messageKey());
            }

            @Override
            public PartyEntryKind fromString(String text) {
                return null;
            }
        });
        comboKind.getSelectionModel().select(PartyEntryKind.COLLECTION);
        comboKind.getSelectionModel().selectedItemProperty()
                .addListener((observable, was, now) -> applyKind(now));
        // Editing a saved movement does not change what kind it is: the column it lives in is
        // already decided, and switching a collection into a note would move money out of a
        // till that has been counted.
        comboKind.setDisable(movementId > 0);
        applyKind(PartyEntryKind.COLLECTION);
    }

    /**
     * What the chosen kind turns off.
     * <p>
     * A note passes through no till, so the treasury, the wallet fee and the invoice allocation
     * have nothing to say about it. They are disabled rather than hidden: the row keeps its place
     * so the form does not jump about as the user changes the kind.
     */
    private void applyKind(PartyEntryKind kind) {
        boolean cash = kind != null && kind.movesCash();
        comboTreasury.setDisable(!cash);
        labelTreasure.setDisable(!cash);
        boolean allocates = kind != null && kind.allowsAllocation();
        comboInvoice.setDisable(!allocates);
        labelInvoice.setDisable(!allocates);
        if (!allocates) {
            comboInvoice.getSelectionModel().select(ON_ACCOUNT);
        }
        if (!cash) {
            showFeeRow(false);
        } else {
            refreshWalletFee();
        }
        // The remainder is computed from the kind as much as from the amount - a debit note adds
        // where a collection subtracts - so changing the kind has to recompute it. It did not:
        // switching a 500 collection to a 500 debit note on a balance of 1,050 left the screen
        // reading 550 where the answer is 1,550, wrong by twice the amount and wrong in the
        // direction that flatters the customer.
        recomputeRest();
    }

    /**
     * The invoice picker, which writes {@code numberInv}.
     * <p>
     * The first entry is "on account", and it is the default: most collections are not against a
     * particular invoice, and every payment in every existing install is one of those. Choosing
     * an invoice shows what it still owes, and the amount is checked against that again at save
     * time - inside the transaction, because a dialog can stay open while another till settles
     * the same invoice.
     */
    private void addInvoiceCombo() {
        comboInvoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(OpenInvoice invoice) {
                if (invoice == null) {
                    return LanguageManager.getInstance().getString("party.payment.on.account");
                }
                return invoice.invoiceNumber() + "  -  " + invoice.date()
                        + "  -  " + moneyText(invoice.remaining());
            }

            @Override
            public OpenInvoice fromString(String text) {
                return null;
            }
        });
        comboInvoice.getSelectionModel().selectedItemProperty()
                .addListener((observable, was, invoice) -> txtAmountInv.setText(
                        invoice == null ? "" : moneyText(invoice.remaining())));
    }

    /** The balance and the open invoices of the party just chosen: two queries, not two scans. */
    private void onPartyChosen(T3 party) {
        code_id = party == null ? 0 : party.getId();
        txtAmountInv.clear();
        if (code_id <= 0) {
            setBalanceBefore(BigDecimal.ZERO);
            comboInvoice.setItems(FXCollections.observableArrayList());
            return;
        }
        try {
            setBalanceBefore(statementService.currentBalance(partyKind(), code_id));
            List<OpenInvoice> invoices = new ArrayList<>();
            invoices.add(ON_ACCOUNT);
            invoices.addAll(allocationService.openInvoices(partyKind(), code_id, movementId));
            comboInvoice.setItems(FXCollections.observableArrayList(invoices));
            comboInvoice.getSelectionModel().select(ON_ACCOUNT);
        } catch (Exception e) {
            logException(e);
        }
    }

    private PartyKind partyKind() {
        return nameAndAccountInterface.partyKind();
    }

    /**
     * Shows the fee row and fills in what this wallet would charge, or hides it.
     * <p>
     * Reads the treasury each time rather than caching: the percentage is edited on another
     * screen, and this one lives for as long as the window is open.
     */
    private void refreshWalletFee() {
        String treasuryName = comboTreasury.getSelectionModel().getSelectedItem();
        if (treasuryName == null || movementId != 0 || !kind().movesCash()) {
            showFeeRow(false);
            return;
        }
        try {
            Treasury treasury = treasuryService.getTreasuryByName(treasuryName);
            BigDecimal percent = treasury == null ? null : treasury.getFeePercent();
            if (percent == null || percent.signum() <= 0) {
                showFeeRow(false);
                return;
            }
            showFeeRow(true);
            txtFee.setText(WalletFee.on(amount(), percent).toPlainString());
        } catch (DaoException e) {
            logException(e);
            showFeeRow(false);
        }
    }

    private void showFeeRow(boolean visible) {
        labelFee.setVisible(visible);
        labelFee.setManaged(visible);
        txtFee.setVisible(visible);
        txtFee.setManaged(visible);
        if (!visible) {
            txtFee.clear();
        }
    }

    /** Zero unless a wallet fee is actually on screen - an invisible field cannot charge anybody. */
    private BigDecimal walletFee() throws UserValidationException {
        if (!txtFee.isVisible() || txtFee.getText() == null || txtFee.getText().isBlank()) {
            return BigDecimal.ZERO;
        }
        BigDecimal fee = BigDecimal.valueOf(DoubleSetting.parseDoubleOrDefault(txtFee.getText()));
        if (fee.signum() != 0 && !WalletFee.isPlausible(amount(), fee)) {
            Platform.runLater(() -> txtFee.requestFocus());
            throw new UserValidationException(
                    LanguageManager.getInstance().getString("treasury.fee.error.too.large"));
        }
        return fee;
    }

    @Override
    public int insertData() throws Exception {
        var lm = LanguageManager.getInstance();
        PartyEntryKind kind = kind();

        if (code_id <= 0) {
            Platform.runLater(() -> partyField.requestFocus());
            throw new UserValidationException(lm.getString("party.payment.party.required"));
        }
        LocalDate value = date.getValue();
        if (value == null || value.isAfter(LocalDate.now())) {
            Platform.runLater(() -> date.requestFocus());
            // It used to refuse a future date with msg.cannot.insert.date.less, which says the
            // opposite of what happened.
            throw new UserValidationException(lm.getString("party.payment.date.future"));
        }
        if (kind.movesCash() && comboTreasury.getSelectionModel().isEmpty()) {
            Platform.runLater(() -> comboTreasury.requestFocus());
            throw new UserValidationException(lm.getString("party.payment.treasury.required"));
        }
        double amount = amount().doubleValue();
        if (amount <= 0) {
            Platform.runLater(() -> txtPaid.requestFocus());
            throw new UserValidationException(lm.getString("party.payment.amount.required"));
        }

        Treasury treasury = treasuryService.getTreasuryByName(
                comboTreasury.getSelectionModel().getSelectedItem());
        OpenInvoice allocated = comboInvoice.getSelectionModel().getSelectedItem();
        int invoiceNumber = allocated == null ? 0 : (int) allocated.invoiceNumber();

        // A new movement carries no number: the database assigns it. An edit carries its own.
        T4 movement = accountData.objectData(movementId, value.toString(),
                kind.paidColumn(amount), txtNotes.getText(), invoiceNumber, code_id, treasury);
        movement.setPurchase(kind.purchaseColumn(amount));

        String correctionReason = "";
        if (movementId > 0) {
            Optional<String> reason = ShiftCorrectionReasonPrompt.forUpdate();
            if (reason.isEmpty()) {
                return 0;
            }
            correctionReason = reason.get();
        }
        return nameAndAccountInterface.saveAccount(movement, walletFee(), correctionReason);
    }

    @Override
    public void afterSaved() {
        if (eventBus != null) {
            eventBus.publish(new AccountChanged(partyKind()));
            eventBus.publish(new TreasuryBalancesChanged());
        }
        txtCode.setText(LanguageManager.getInstance().getString("party.payment.code.automatic"));
        txtPaid.setText("0");
        txtNotes.clear();
        // The balance and the open invoices both moved, so both are read again.
        onPartyChosen(partyField.chosenPartyProperty().get());
    }

    @Override
    public void selectData() {
        partyField.setDisable(true);
        try {
            T4 stored = accountData.getAccountByNum(movementId);
            if (stored == null) {
                return;
            }
            code_id = accountData.getIdName(stored);
            partyField.select(nameAndAccountInterface.getNameById(code_id));
            boolean note = stored.getPurchase() != 0;
            comboKind.getSelectionModel().select(note
                    ? (stored.getPurchase() > 0 ? PartyEntryKind.DEBIT_NOTE : PartyEntryKind.CREDIT_NOTE)
                    : PartyEntryKind.COLLECTION);
            txtCode.setText(String.valueOf(stored.getId()));
            txtPaid.setText(String.valueOf(note
                    ? Math.abs(stored.getPurchase()) : stored.getPaid()));
            date.setValue(LocalDate.parse(stored.getDate()));
            txtNotes.setText(stored.getNotes());
            selectTreasury(stored.getTreasury().getName());
            selectAllocatedInvoice(stored.getInvoice_number());
        } catch (Exception e) {
            logException(e);
        }
    }

    /**
     * Selects the invoice a saved payment was put against.
     * <p>
     * The open-invoice list is read with this movement excluded, so an invoice that this very
     * payment settled in full is still on the list - otherwise opening a saved payment would
     * silently reset it to "on account" and saving again would unallocate it.
     */
    private void selectAllocatedInvoice(int invoiceNumber) {
        if (invoiceNumber <= 0) {
            return;
        }
        comboInvoice.getItems().stream()
                .filter(invoice -> invoice != null && invoice.invoiceNumber() == invoiceNumber)
                .findFirst()
                .ifPresent(invoice -> comboInvoice.getSelectionModel().select(invoice));
    }

    @Override
    public void resetData() {
    }

    @NotNull
    @Override
    public BooleanBinding checkDataToEnableButton() {
        return partyField.chosenPartyProperty().isNull()
                .or(Bindings.createBooleanBinding(
                        () -> kind().movesCash() && comboTreasury.getSelectionModel().isEmpty(),
                        comboKind.getSelectionModel().selectedItemProperty(),
                        comboTreasury.getSelectionModel().selectedItemProperty()));
    }

    private PartyEntryKind kind() {
        PartyEntryKind selected = comboKind.getSelectionModel().getSelectedItem();
        return selected == null ? PartyEntryKind.COLLECTION : selected;
    }

    private BigDecimal amount() {
        return BigDecimal.valueOf(DoubleSetting.parseDoubleOrDefault(txtPaid.getText()));
    }

    /**
     * Records what the party owes now, and shows it.
     * <p>
     * The remainder is recomputed here rather than from a listener on the display: the balance is
     * written once, when a party is chosen, and that used to happen <b>before</b> the listener that
     * watched the field was attached - {@code addPartyField()} runs at the top of
     * {@code otherSetting()} and the listeners were added at the bottom. So the screen opened on a
     * customer owing 1,050 with the remainder reading 0.0, and corrected itself only when somebody
     * typed. A value that is set in one place does not need watching; it needs the recompute on the
     * same line.
     */
    private void setBalanceBefore(BigDecimal balance) {
        balanceBefore = balance == null ? BigDecimal.ZERO : balance;
        txtBalance.setText(moneyText(balanceBefore));
        recomputeRest();
    }

    /**
     * What the party will owe after this movement.
     * <p>
     * {@code Double.parseDouble(txtBalance.getText())} stood here, and a party with no movements
     * left that field blank - so typing into the amount box threw a {@code NumberFormatException}
     * and reached the user as a technical error with a reference code. Reading the field at all is
     * what {@link #balanceBefore} now replaces.
     */
    private void recomputeRest() {
        PartyEntryKind kind = kind();
        BigDecimal change = kind.movesCash()
                ? amount().negate()
                : BigDecimal.valueOf(kind.purchaseColumn(amount().doubleValue()));
        txtAmount.setText(moneyText(MoneyMath.add(balanceBefore, change)));
    }

    /**
     * How every amount on this screen is written: two decimals, thousands separated.
     * <p>
     * {@code Columns.money} is the one definition of that, and a label has to agree with the column
     * it stands beside - the balances screen writes this party's 1,050.00 and this screen was
     * writing 1050.0 for the same row.
     */
    private static String moneyText(BigDecimal value) {
        return com.hamza.controlsfx.table.Columns.money(value);
    }

    private void addTreasurySetting() {
        List<String> names = new ArrayList<>();
        try {
            names = treasuryService.listTreasuryModelNames();
        } catch (DaoException e) {
            logException(e);
        }
        comboTreasury.setItems(FXCollections.observableArrayList(names));
        comboTreasury.getSelectionModel().selectFirst();
    }

    /**
     * Selects the treasury a saved movement was entered against, adding its name to the list
     * first when it is not there.
     * <p>
     * The picker offers active treasuries only, and a treasury may be closed after documents have
     * been entered against it - it keeps its history and simply leaves the pickers. Without this,
     * opening such a movement would silently show whichever treasury happened to be first and
     * save it back under that one.
     */
    private void selectTreasury(String treasuryName) {
        if (treasuryName == null || treasuryName.isBlank()) {
            return;
        }
        if (!comboTreasury.getItems().contains(treasuryName)) {
            comboTreasury.getItems().add(treasuryName);
        }
        comboTreasury.getSelectionModel().select(treasuryName);
    }

    private void logException(Exception e) {
        AllAlerts.handleError(
                LanguageManager.getInstance().getString("party.error.save.account.movement"), e);
    }
}
