package com.hamza.account.controller.name_account;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.AccountChanged;
import com.hamza.account.features.events.NameChanged;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Area;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.model.domain.SelPriceTypeModel;
import com.hamza.account.opening.OpeningBalanceGuard;
import com.hamza.account.opening.OpeningBalanceRegistry;
import com.hamza.account.opening.OpeningBalanceRule;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.AreaService;
import com.hamza.account.features.employee.EmployeeScope;
import com.hamza.account.features.employee.EmployeeService;
import com.hamza.account.service.SelPriceItemService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.others.DoubleSetting;
import com.hamza.controlsfx.others.Utils;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.AnchorPane;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.hamza.controlsfx.others.TextFormat.createNumericTextFormatter;
import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * One customer or one supplier.
 * <p>
 * <b>Built in code, and its FXML is down to a root node</b> - the same treatment the
 * statement and the balances screens were given, for the same reason. Every caption in
 * that file was an English placeholder this class replaced at runtime, so a label it
 * forgot would ship reading {@code "area"}; the rows were numbered in one file and
 * referred to by number in another; and V56's six new fields would have meant
 * renumbering nine rows in one place and matching them in the other.
 * <p>
 * <b>The fields V56 added are on screen here, and one of them is not what it looks
 * like.</b> {@code opening_balance_date} is dated evidence about a figure that is
 * otherwise undated, so it is locked by {@link OpeningBalanceGuard} exactly as the
 * amount is: once the party has moved, re-dating the opening entry moves it through the
 * history precisely as rewriting its amount would, and a screen that greyed one while
 * leaving the other typable would be a way round the guard reachable from the guard's
 * own screen. {@code PartyTableSpec.openingColumns()} drops both from the statement, so
 * this is a hint over a rule rather than the rule itself.
 *
 * @param <T3> the party - Customers or Suppliers
 * @param <T4> its account
 */
@FxmlPath(pathFile = "addName.fxml")
@Log4j2
public class AddNameController<T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T3, T4> implements AddInterface {

    /** What {@code default_delegate_id} holds for a customer with no standing delegate. */
    private static final Employees NO_DELEGATE = null;

    private final int id;
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final AreaService areaService = ServiceRegistry.get(AreaService.class);
    private final SelPriceItemService selPriceItemService = ServiceRegistry.get(SelPriceItemService.class);
    private final EmployeeService employeeService = ServiceRegistry.get(EmployeeService.class);

    private LocalDateTime loadedUpdatedAt;

    /**
     * The row as it was read, kept so a save carries forward what this screen does not
     * show. Null for a new party.
     * <p>
     * The update writes every column of the row, so a field absent from the form is
     * written back as whatever the model happens to hold - which for a freshly built
     * model is the type's default. That is how a screen with no control for
     * {@code user_activity} came to reactivate every account it touched
     * ({@code UsersService.update}).
     */
    private T3 loaded;

    @FXML
    private VBox formRoot;

    @FXML
    private AnchorPane screenRoot;

    private final TextField txtCode = new TextField();
    private final TextField txtName = new TextField();
    private final TextField txtTel = new TextField();
    private final TextField txtEmail = new TextField();
    private final TextField txtAddress = new TextField();
    private final TextField txtTaxNumber = new TextField();
    private final TextField txtBalance = new TextField();
    private final TextField txtLimit = new TextField();
    private final TextField txtTerms = new TextField();
    private final TextArea txtOther = new TextArea();
    private final DatePicker openingDate = new DatePicker();
    private final ComboBox<String> comboSelPrice = new ComboBox<>();
    private final ComboBox<String> comboArea = new ComboBox<>();
    private final ComboBox<Employees> comboDelegate = new ComboBox<>();
    private final CheckBox chkActive = new CheckBox();

    public AddNameController(DataInterface<?, ?, T3, T4> dataInterface,
                             DaoFactory daoFactory, DataPublisher dataPublisher,
                             int id) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.id = id;
    }

    @Override
    public String dialogStyleClass() {
        return PartyScreenIdentity.forKind(dataInterface.designInterface().showDataForCustomer()
                ? PartyKind.CUSTOMER : PartyKind.SUPPLIER).styleClass();
    }

    @FXML
    public void initialize() {
        otherSetting();
        resetData();
        selectData();
    }

    // ---- the form ------------------------------------------------------------------

    @Override
    public void otherSetting() {
        boolean customer = dataInterface.designInterface().showDataForCustomer();

        txtCode.setEditable(false);
        setTextFormatter(txtBalance, txtLimit);
        txtTel.setTextFormatter(createNumericTextFormatter());
        txtTerms.setTextFormatter(createNumericTextFormatter());
        txtOther.setPrefRowCount(3);
        DateSetting.dateAction(openingDate);

        chkActive.setText(text("party.active"));
        chkActive.setSelected(true);
        chkActive.setTooltip(new Tooltip(text("party.active.tip")));
        txtTerms.setTooltip(new Tooltip(text("party.payment.terms.tip")));
        openingDate.setTooltip(new Tooltip(text("party.opening.date.tip")));

        fillPriceTiers();
        fillAreas();
        fillDelegates();

        // The credit limit, the price tier and the standing delegate are the customer's
        // three columns; a supplier's row has none of them - see PartyTableSpec.
        for (Node customerOnly : new Node[]{txtLimit, comboSelPrice, comboDelegate}) {
            customerOnly.setDisable(!customer);
        }

        PartyFormProfile profile = PartyScreenIdentity
                .forKind(customer ? PartyKind.CUSTOMER : PartyKind.SUPPLIER)
                .formProfile(id > 0);
        screenRoot.getStyleClass().add(profile.styleClass());

        ScrollPane scroll = scrolling(columns(customer));
        formRoot.getChildren().setAll(PartyIdentityHeader.of(profile), scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // One declared order through the form - rule ق-ل9, pinned by
        // KeyboardNavigationArchitectureTest.
        whenEnterPressed(txtName, txtTel, txtEmail, txtAddress, txtTaxNumber,
                txtBalance, txtLimit, txtTerms, txtOther);
        Platform.runLater(txtName::requestFocus);
    }

    /**
     * The two halves of a party, side by side and wrapping.
     * <p>
     * A {@code FlowPane} rather than an {@code HBox}: this opens in a dialog the user
     * sizes, and an {@code HBox} squeezes its columns until the captions are cut off -
     * which is what the statement's filter bar did before it was changed. Wrapping puts
     * the second card under the first instead.
     */
    private FlowPane columns(boolean customer) {
        GridPane contact = card(text("party.section.contact"),
                row("code", txtCode),
                row("name", txtName),
                row("tel", txtTel),
                row("party.email", txtEmail),
                row("address", txtAddress),
                row("party.area", comboArea),
                row("others", txtOther));

        List<Node[]> accountRows = new ArrayList<>();
        accountRows.add(row("firstBalance", txtBalance));
        accountRows.add(row("party.opening.date", openingDate));
        if (customer) {
            accountRows.add(row("column.limit", txtLimit));
            accountRows.add(row("selPrice", comboSelPrice));
        }
        accountRows.add(row("party.payment.terms", txtTerms));
        accountRows.add(row("party.tax.number", txtTaxNumber));
        if (customer) {
            accountRows.add(row("party.delegate.default", comboDelegate));
        }
        accountRows.add(row(null, chkActive));

        GridPane account = card(text("party.section.account"),
                accountRows.toArray(Node[][]::new));

        FlowPane pane = new FlowPane(16, 16, contact, account);
        pane.setAlignment(Pos.TOP_CENTER);
        pane.setPadding(new Insets(4));
        // Wide enough for the two cards to sit side by side, which is what decides the dialog's
        // shape: a FlowPane's preferred width is its wrap length, and without one it reports the
        // width of a single card - so the two stacked and the dialog opened 1,070px tall on a
        // 1,080px screen, with the save button on the very edge of it. Wrapping is still what
        // happens when the user narrows the window, which is the reason it is a FlowPane.
        pane.setPrefWrapLength(CARD_WIDTH * 2 + 48);
        return pane;
    }

    /**
     * The width of one card's field column. The two cards plus the gaps decide the dialog's width,
     * so this is the number that keeps it a two-column form rather than a tower.
     */
    private static final double CARD_WIDTH = 330;

    /** A titled group of rows. */
    private GridPane card(String title, Node[]... rows) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("app-card");
        grid.getStyleClass().add("party-form-card");
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(12));

        Label heading = new Label(title);
        heading.getStyleClass().add("app-section-title");
        grid.add(heading, 0, 0, 2, 1);

        for (int index = 0; index < rows.length; index++) {
            Node[] entry = rows[index];
            if (entry[0] == null) {
                grid.add(entry[1], 0, index + 1, 2, 1);
            } else {
                grid.add(entry[0], 0, index + 1);
                grid.add(entry[1], 1, index + 1);
            }
        }

        ColumnConstraints labels = new ColumnConstraints();
        ColumnConstraints fields = new ColumnConstraints();
        fields.setPrefWidth(CARD_WIDTH - 110);
        fields.setHgrow(Priority.SOMETIMES);
        grid.getColumnConstraints().addAll(labels, fields);
        return grid;
    }

    /** A caption and its control; a null key means the control speaks for itself. */
    private Node[] row(String titleKey, Node control) {
        if (control instanceof TextField field && titleKey != null) {
            field.setPromptText(text(titleKey));
        }
        if (control instanceof ComboBox<?> combo) {
            combo.setMaxWidth(Double.MAX_VALUE);
            if (titleKey != null) {
                combo.setPromptText(text(titleKey));
            }
        }
        return new Node[]{titleKey == null ? null : new Label(text(titleKey)), control};
    }

    /**
     * Keeps the form inside the screen.
     * <p>
     * The dialog sizes itself to its content, so without a ceiling a form that grows by a field
     * grows the window - and a window taller than the display puts its own save button off the
     * bottom, where nothing can reach it. The scroll bar is the thing that is allowed to appear
     * instead.
     */
    private ScrollPane scrolling(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        // A ceiling, not a floor: prefViewportHeight would reserve the whole 620 and leave a band
        // of empty card under a short form. Only a form taller than this scrolls.
        scroll.setMaxHeight(MAX_FORM_HEIGHT);
        return scroll;
    }

    /** Leaves room for the title bar and the dialog's own buttons on a 768-tall display. */
    private static final double MAX_FORM_HEIGHT = 620;

    // ---- filling the pickers --------------------------------------------------------

    private void fillPriceTiers() {
        List<SelPriceTypeModel> tiers = getPriceType();
        comboSelPrice.getItems().setAll(tiers.stream().map(SelPriceTypeModel::getName).toList());
        tiers.stream().filter(tier -> tier.getId() == 1).map(SelPriceTypeModel::getName)
                .findFirst()
                .ifPresent(name -> comboSelPrice.getSelectionModel().select(name));
    }

    private void fillAreas() {
        comboArea.getItems().setAll(getAreas().stream().map(Area::getArea_name).toList());
        comboArea.getSelectionModel().selectFirst();
    }

    /**
     * The delegates, with "no delegate" first and selected.
     * <p>
     * It is the default because it is what every existing row holds: the column is
     * {@code DEFAULT 0}, and a customer who has never been assigned one has not been
     * assigned the first name in the list.
     */
    private void fillDelegates() {
        comboDelegate.setConverter(new StringConverter<>() {
            @Override
            public String toString(Employees delegate) {
                return delegate == null ? text("party.delegate.none") : delegate.getName();
            }

            @Override
            public Employees fromString(String value) {
                return null;
            }
        });
        List<Employees> delegates = new ArrayList<>();
        delegates.add(NO_DELEGATE);
        try {
            delegates.addAll(employeeService.delegates(EmployeeScope.ACTIVE_ONLY));
        } catch (DaoException e) {
            log.error("Failed to read the delegates", e);
        }
        comboDelegate.setItems(FXCollections.observableArrayList(delegates));
        comboDelegate.getSelectionModel().select(NO_DELEGATE);
    }

    // ---- saving ---------------------------------------------------------------------

    @Override
    public int insertData() throws Exception {
        SelPriceTypeModel tier = daoFactory.getItemsSelPriceDao()
                .getDataByString(comboSelPrice.getSelectionModel().getSelectedItem());

        Area area = getAreas().stream()
                .filter(candidate -> candidate.getArea_name()
                        .equals(comboArea.getSelectionModel().getSelectedItem()))
                .findFirst()
                .orElseGet(Area::new);

        T3 party = nameData.objectT(txtName.getText(), txtTel.getText(), txtAddress.getText(),
                txtOther.getText(), number(txtLimit), number(txtBalance), tier, area);

        party.setEmail(blankToNull(txtEmail.getText()));
        party.setTax_number(blankToNull(txtTaxNumber.getText()));
        party.setPayment_terms_days(intOrZero(txtTerms.getText()));
        party.setOpening_balance_date(openingDate.getValue());
        party.setActive(chkActive.isSelected());
        if (party instanceof Customers customer) {
            Employees delegate = comboDelegate.getSelectionModel().getSelectedItem();
            customer.setDefault_delegate_id(delegate == null ? 0 : delegate.getId());
        }
        carryForwardWhatIsNotOnScreen(party);

        party.setId(id > 0 ? id : 0);
        if (id > 0) {
            party.setUpdated_at(loadedUpdatedAt);
        }
        return dataInterface.nameAndAccountInterface().saveName(party);
    }

    /**
     * Copies onto the outgoing row anything the stored one holds that this form has no
     * control for.
     * <p>
     * The update writes the whole row, so a column the screen does not show is written
     * back as the model's default rather than left alone. It is one method here and a
     * silently cleared column otherwise; {@code UsersService.update} carries
     * {@code user_activity} across for exactly this reason, after a screen with no
     * control for it reactivated every account it saved.
     */
    private void carryForwardWhatIsNotOnScreen(T3 party) {
        if (loaded == null || dataInterface.designInterface().showDataForCustomer()) {
            return;
        }
        if (party instanceof Customers outgoing && loaded instanceof Customers stored) {
            outgoing.setCredit_limit(stored.getCredit_limit());
            outgoing.setSelPriceObject(stored.getSelPriceObject());
            outgoing.setDefault_delegate_id(stored.getDefault_delegate_id());
        }
    }

    @Override
    public void afterSaved() {
        if (eventBus != null) {
            PartyKind kind = nameAndAccountInterface.partyKind();
            eventBus.publish(new NameChanged(kind));
            eventBus.publish(new AccountChanged(kind));
        }
        resetData();
        Platform.runLater(txtName::requestFocus);
    }

    /** New customer/supplier entries commonly arrive in batches; edits do not. */
    @Override
    public boolean keepDialogOpenAfterSave() {
        return id == 0;
    }

    // ---- reading --------------------------------------------------------------------

    /**
     * The party being edited.
     * <p>
     * One query. It used to read {@code nameList()} - every customer in the database,
     * each one resolving its area and its price tier with a query of its own - and then
     * {@code filter(e -> e.getId() == id)} in Java, to edit one row.
     */
    @Override
    public void selectData() {
        if (id <= 0) {
            return;
        }
        T3 party;
        try {
            party = nameAndAccountInterface.getNameById(id);
        } catch (Exception e) {
            log.error("Failed to read party {}", id, e);
            return;
        }
        if (party == null) {
            return;
        }
        loaded = party;
        loadedUpdatedAt = party.getUpdated_at();

        txtCode.setText(String.valueOf(party.getId()));
        txtName.setText(party.getName());
        txtTel.setText(party.getTel());
        txtAddress.setText(party.getAddress());
        txtOther.setText(party.getNotes());
        txtBalance.setText(String.valueOf(party.getFirst_balance()));
        txtLimit.setText(String.valueOf(nameData.limit(party)));
        txtEmail.setText(party.getEmail());
        txtTaxNumber.setText(party.getTax_number());
        txtTerms.setText(String.valueOf(party.getPayment_terms_days()));
        openingDate.setValue(party.getOpening_balance_date());
        chkActive.setSelected(party.isActive());
        if (party.getArea() != null) {
            comboArea.getSelectionModel().select(party.getArea().getArea_name());
        }

        if (dataInterface.designInterface().showDataForCustomer()) {
            comboSelPrice.getSelectionModel().select(nameData.getPriceType(party));
            selectDelegate(party);
        }
        lockOpeningBalanceIfMoved(party.getId());
    }

    private void selectDelegate(T3 party) {
        if (!(party instanceof Customers customer) || customer.getDefault_delegate_id() <= 0) {
            comboDelegate.getSelectionModel().select(NO_DELEGATE);
            return;
        }
        comboDelegate.getItems().stream()
                .filter(delegate -> delegate != null
                        && delegate.getId() == customer.getDefault_delegate_id())
                .findFirst()
                .ifPresentOrElse(
                        delegate -> comboDelegate.getSelectionModel().select(delegate),
                        () -> comboDelegate.getSelectionModel().select(NO_DELEGATE));
    }

    /**
     * Greys the opening balance <b>and the day it is as at</b> once this party has
     * moved, and says why.
     * <p>
     * The rule is applied where the row is written - {@code CustomerDao.update} and
     * {@code SuppliersDao.update} drop both columns through
     * {@code PartyTableSpec.openingColumns()} - because a disabled field is a hint and
     * the same save is reachable from anything holding the DAO. This is so the user
     * finds out before typing rather than by having the save refused afterwards.
     * <p>
     * The screen serves both sides, so which rule applies comes from
     * {@code partyKind()}, the same answer the events are filtered on. A failure to read
     * it leaves the fields enabled: the DAO still refuses the change, so the worst case
     * is a message at the wrong moment rather than a rewritten history.
     */
    private void lockOpeningBalanceIfMoved(int partyId) {
        try {
            OpeningBalanceRule rule = nameAndAccountInterface.partyKind() == PartyKind.CUSTOMER
                    ? OpeningBalanceRegistry.CUSTOMERS
                    : OpeningBalanceRegistry.SUPPLIERS;

            if (!OpeningBalanceGuard.shared().isLocked(rule, partyId)) {
                unlockOpeningBalance();
                return;
            }
            Tooltip why = new Tooltip(text("party.balance.locked.tooltip", rule.correction()));
            for (Control locked : new Control[]{txtBalance, openingDate}) {
                locked.setDisable(true);
                locked.setTooltip(why);
            }
        } catch (DaoException e) {
            log.error("Failed to read the opening-balance lock for {}", partyId, e);
        }
    }

    private void unlockOpeningBalance() {
        txtBalance.setDisable(false);
        txtBalance.setTooltip(null);
        openingDate.setDisable(false);
        openingDate.setTooltip(null);
    }

    @Override
    public void resetData() {
        loaded = null;
        loadedUpdatedAt = null;
        txtCode.setText(text("item.code.generate"));
        txtLimit.setText("0");
        txtBalance.setText("0");
        txtTerms.setText("0");
        txtOther.clear();
        Utils.clearAll(txtName, txtTel, txtAddress, txtEmail, txtTaxNumber);
        // A new party's opening balance is as at today unless somebody says otherwise,
        // and it is a new row, so it has moved nothing.
        openingDate.setValue(LocalDate.now());
        chkActive.setSelected(true);
        comboDelegate.getSelectionModel().select(NO_DELEGATE);
        unlockOpeningBalance();
    }

    /**
     * <b>The result of {@code or} is the binding, not a change to the receiver.</b> This
     * read {@code binding.or(...)} on a line of its own and returned {@code binding}, so
     * the price-tier half was computed and thrown away and the save button asked about
     * the name alone.
     */
    @NotNull
    @Override
    public BooleanBinding checkDataToEnableButton() {
        BooleanBinding nameIsEmpty = txtName.textProperty().isEmpty();
        if (!dataInterface.designInterface().showDataForCustomer()) {
            return nameIsEmpty;
        }
        return Bindings.or(nameIsEmpty,
                comboSelPrice.getSelectionModel().selectedItemProperty().isNull());
    }

    // ---- small helpers ---------------------------------------------------------------

    /**
     * A money field's value. {@code Double.parseDouble} stood here and threw on an empty
     * box, which reaches the user as a technical error with a reference code rather than
     * as anything they can act on.
     */
    private static double number(TextField field) {
        return DoubleSetting.parseDoubleOrDefault(field.getText());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int intOrZero(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    private static String text(String key, Object... args) {
        return args.length == 0
                ? LanguageManager.getInstance().getString(key)
                : LanguageManager.getInstance().getString(key, args);
    }

    private List<Area> getAreas() {
        try {
            return areaService.fetchAllAreas();
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
            return List.of();
        }
    }

    private List<SelPriceTypeModel> getPriceType() {
        try {
            return selPriceItemService.getSelPriceTypeList();
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
            return List.of();
        }
    }
}
