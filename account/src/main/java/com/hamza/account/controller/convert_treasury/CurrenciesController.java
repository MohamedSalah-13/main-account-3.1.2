package com.hamza.account.controller.convert_treasury;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.CurrencyConversion;
import com.hamza.account.features.currency.CurrencyConverter;
import com.hamza.account.features.currency.CurrencyDraft;
import com.hamza.account.features.currency.CurrencyFormat;
import com.hamza.account.features.currency.CurrencyService;
import com.hamza.account.features.currency.ExchangeRate;
import com.hamza.account.features.currency.ExchangeRateDraft;
import com.hamza.account.features.currency.RateHistoryLine;
import com.hamza.account.features.currency.RateInForce;
import com.hamza.account.features.events.CurrenciesChanged;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.table.RowDetailDrawer;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import com.hamza.controlsfx.table.Columns;
import com.hamza.controlsfx.table.columnEdit.NumberTextConverter;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * The currencies the shop deals in, what each is worth, and a converter (docs/currency-plan.md, phase A).
 * <p>
 * <b>Two tabs, because they are two questions.</b> The first is the catalogue - which currencies exist,
 * which one the books are in, and each one's rate today - with a currency's rate history in a
 * {@link RowDetailDrawer} opened from its row: the list and its rates are a master and its detail, and
 * the gesture is the one the merge screen and the transfer history use - open a row, read it, open the
 * next. The second is the converter: an amount of one currency written in every other at a day's rates.
 * <p>
 * <b>The base currency is shown apart, in a sentence.</b> It is not a row like the others: it is what
 * every amount in the books is in, it has no rate because it is worth one of itself, and it moves only
 * while no rate is recorded (ق-٦). A row saying "rate: 1" beside rows saying "48.5" would be read as one
 * more currency with an odd rate.
 * <p>
 * It holds no rule: what a currency or a rate may be is {@code CurrencyRules}' and
 * {@code ExchangeRateRules}', asked by {@link CurrencyService}. The permissions here are hints in the
 * {@code isGranted} sense; the service asks {@code require} on every write.
 */
@FxmlPath(pathFile = "treasury/currencies.fxml")
public class CurrenciesController {

    private static final PseudoClass STOPPED = PseudoClass.getPseudoClass("stopped");
    private static final PseudoClass BASE = PseudoClass.getPseudoClass("base-currency");
    private static final String ACTIONS_COLUMN = "currencyActions";
    /** Keeps a date reading 2026-09-23 after an Arabic word; see StockTransferHistoryView. */
    private static final String LEFT_TO_RIGHT_MARK = "\u200E";

    private final CurrencyService service;
    private final EventBus eventBus;
    private final Subscriptions subscriptions = new Subscriptions();

    @FXML
    private StackPane root;

    // ---- the catalogue ---------------------------------------------------------------------
    private final AnchorPane currenciesHost = new AnchorPane();
    private final TableView<Currency> table = new TableView<>();
    private final ContentSizedColumns<Currency> widths = new ContentSizedColumns<>();
    private final Label baseLabel = new Label();
    private final TextField txtCode = new TextField();
    private final TextField txtName = new TextField();
    private final TextField txtSymbol = new TextField();
    private final TextField txtDecimals = new TextField();
    private final TextField txtSort = new TextField();
    private final CheckBox checkActive = new CheckBox(text("currency.active"));
    private final Button btnSave = new Button(text("save"));
    private final Button btnNew = new Button(text("currency.new"));
    private final Label editingLabel = new Label();
    /** The currency the form is editing; 0 while it is adding one. */
    private int editing;

    private Currency base;
    private Map<Integer, RateInForce> ratesToday = Map.of();

    // ---- one currency's rates ----------------------------------------------------------------
    private RowDetailDrawer drawer;
    private final TableView<RateHistoryLine> ratesTable = new TableView<>();
    private final ContentSizedColumns<RateHistoryLine> rateWidths = new ContentSizedColumns<>();
    private final DatePicker rateDate = new DatePicker();
    private final TextField txtRate = new TextField();
    private final TextField txtNotes = new TextField();
    private final Label rateCaption = new Label();
    private final Label rateUnit = new Label();
    private final Button btnSaveRate = new Button(text("save"));
    private final Button btnNewRate = new Button(text("currency.rate.new"));
    private final Label rateEditingLabel = new Label();
    /** The currency whose rates the drawer shows. */
    private Currency ratesOf;
    /** The rate the drawer's form is editing; 0 while it is adding one. */
    private int editingRate;

    // ---- the converter -------------------------------------------------------------------------
    private final TextField txtAmount = new TextField();
    private final ComboBox<Currency> comboFrom = new ComboBox<>();
    private final DatePicker convertDate = new DatePicker(LocalDate.now());
    private final Button btnConvert = new Button(text("currency.convert.run"));
    private final TableView<CurrencyConversion> conversions = new TableView<>();
    private final ContentSizedColumns<CurrencyConversion> conversionWidths = new ContentSizedColumns<>();

    public CurrenciesController() {
        this.service = ServiceRegistry.get(CurrencyService.class);
        this.eventBus = ServiceRegistry.get(EventBus.class);
    }

    @FXML
    private void initialize() {
        // The palette class the identity header reads its colours from (theme-light.css, theme-dark.css).
        // Without one the header's looked-up colours resolve to nothing and its white title is white on
        // the page - which is how the first rendering of this screen came out.
        root.getStyleClass().add("screen-currencies");
        TabPane tabs = new TabPane(catalogueTab(), converterTab());
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        root.getChildren().setAll(tabs);
        reload();

        // A rate recorded at another till has to reach this one, or two machines convert one amount two
        // ways. The relay turns another machine's change into this event; the handle is closed with the
        // screen rather than left on a process-wide bus.
        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(CurrenciesChanged.class, event -> reload()));
        }
        subscriptions.disposeWith(root);
    }

    // ==========================================================================================
    // The catalogue
    // ==========================================================================================

    private Tab catalogueTab() {
        buildTable();
        VBox content = new VBox(10, header(), baseCard(), entryCard(), table);
        content.setPadding(new Insets(10));
        VBox.setVgrow(table, Priority.ALWAYS);
        anchor(content);
        currenciesHost.getChildren().add(content);
        buildRatesDrawer();

        Tab tab = new Tab(text("currency.tab.currencies"), currenciesHost);
        tab.setGraphic(AppIcon.CURRENCY.graphic(16));
        return tab;
    }

    private Node header() {
        Label title = new Label(text("currency.title"));
        title.getStyleClass().add("party-screen-title");
        Label subtitle = new Label(text("currency.subtitle"));
        subtitle.getStyleClass().add("party-screen-subtitle");
        subtitle.setWrapText(true);
        VBox captions = new VBox(3, title, subtitle);
        HBox bar = new HBox(12, AppIcon.CURRENCY.graphic(24), captions);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("party-screen-header");
        return bar;
    }

    /** Which currency the books are in, as a sentence - see the class comment. */
    private Node baseCard() {
        baseLabel.getStyleClass().add("form-label");
        Label hint = new Label(text("currency.base.hint"));
        hint.getStyleClass().add("form-hint");
        hint.setWrapText(true);
        VBox card = new VBox(4, baseLabel, hint);
        card.getStyleClass().add("app-card");
        card.setPadding(new Insets(8));
        return card;
    }

    private Node entryCard() {
        txtCode.setPromptText("USD");
        txtCode.setPrefWidth(70);
        txtName.setPromptText(text("currency.name.prompt"));
        txtName.setPrefWidth(180);
        txtSymbol.setPromptText("$");
        txtSymbol.setPrefWidth(70);
        txtDecimals.setPrefWidth(50);
        txtSort.setPrefWidth(60);
        checkActive.setSelected(true);
        btnSave.setGraphic(AppIcon.SAVE.graphic());
        btnSave.getStyleClass().add("app-primary-button");
        btnSave.setOnAction(event -> saveCurrency());
        btnNew.setGraphic(AppIcon.ADD.graphic());
        btnNew.getStyleClass().add("app-neutral-button");
        btnNew.setOnAction(event -> resetCurrencyForm());
        editingLabel.getStyleClass().add("form-hint");
        whenEnterPressed(txtCode, txtName, txtSymbol, txtDecimals, txtSort, btnSave);

        // A FlowPane, not an HBox: at 1366 points six fields, a check box and two buttons do not fit on
        // one line, and a row whose children may not shrink is a row wider than the window.
        FlowPane row = new FlowPane(8, 6,
                caption("currency.code"), txtCode, caption("currency.name"), txtName,
                caption("currency.symbol"), txtSymbol, caption("currency.decimals"), txtDecimals,
                caption("currency.sort"), txtSort, checkActive, btnSave, btnNew, editingLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(6, row);
        card.getStyleClass().addAll("app-card", "party-form-card");
        card.setPadding(new Insets(8));
        // A hint, not the guard: the service asks currency.update on every write.
        card.setDisable(!AuthorizationGuard.isGranted(AppPermissions.CURRENCY_UPDATE));
        resetCurrencyForm();
        return card;
    }

    private void buildTable() {
        table.setId("currencies");
        table.setPlaceholder(new Label(text("currency.empty")));
        TableColumn<Currency, Void> actions = RowActionsColumn.of("currency.column.actions",
                RowAction.permitted(List.of(
                        RowAction.of("update", AppIcon.EDIT, "app-primary-button",
                                AppPermissions.CURRENCY_UPDATE, this::edit),
                        RowAction.of("currency.rates", AppIcon.HISTORY, "app-neutral-button",
                                AppPermissions.CURRENCY_SHOW, this::showRates),
                        RowAction.of("currency.make.base", AppIcon.PIN, "app-neutral-button",
                                AppPermissions.CURRENCY_UPDATE, this::makeBase),
                        RowAction.of("currency.toggle", AppIcon.SECURITY, "app-neutral-button",
                                AppPermissions.CURRENCY_UPDATE, this::toggleActive),
                        RowAction.of("delete", AppIcon.DELETE, "app-neutral-button",
                                AppPermissions.CURRENCY_UPDATE, this::remove))));
        actions.setId(ACTIONS_COLUMN);
        table.getColumns().setAll(List.of(
                actions,
                withId("currencyCode", Columns.text("currency.column.code", Currency::code)),
                withId("currencyName", Columns.text("currency.column.name", Currency::name)),
                withId("currencySymbol", Columns.text("currency.column.symbol", Currency::symbol)),
                withId("currencyDecimals", Columns.number("currency.column.decimals", Currency::decimalPlaces)),
                withId("currencyBase", Columns.text("currency.column.base",
                        currency -> currency.base() ? text("yes") : "")),
                withId("currencyActive", Columns.text("currency.column.active",
                        currency -> text(currency.active() ? "yes" : "no"))),
                withId("currencyRate", figure("currency.column.rate", this::rateText)),
                withId("currencyRateDate", Columns.date("currency.column.rate.date",
                        currency -> rateOf(currency) == null ? null : rateOf(currency).effectiveDate())),
                withId("currencyChange", figure("currency.column.change", currency -> rateOf(currency) == null
                        ? "" : CurrencyFormat.change(rateOf(currency).changePercent()))),
                withId("currencyInverse", figure("currency.column.inverse", this::inverseText))));
        table.setRowFactory(view -> {
            TableRow<Currency> row = new TableRow<>() {
                @Override
                protected void updateItem(Currency currency, boolean empty) {
                    super.updateItem(currency, empty);
                    pseudoClassStateChanged(STOPPED, !empty && currency != null && !currency.active());
                    pseudoClassStateChanged(BASE, !empty && currency != null && currency.base());
                }
            };
            // The shortcut for the row's own rates button, as on the merge screen.
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty() && !row.getItem().base()) {
                    showRates(row.getItem());
                }
            });
            return row;
        });
        widths.install(table);
    }

    /** The rate in force today, or nothing - for the base, and for a currency with no rate yet. */
    private RateInForce rateOf(Currency currency) {
        return currency.base() ? null : ratesToday.get(currency.id());
    }

    private String rateText(Currency currency) {
        if (currency.base()) {
            return "1";
        }
        RateInForce rate = rateOf(currency);
        return rate == null ? text("currency.rate.none") : CurrencyFormat.rate(rate.rate());
    }

    /** What one unit of the base buys of this currency - how a shop with a strong base reads a rate. */
    private String inverseText(Currency currency) {
        RateInForce rate = rateOf(currency);
        return rate == null ? "" : CurrencyFormat.indicative(CurrencyConverter.inverse(rate.rate()));
    }

    // ---- the form ----------------------------------------------------------------------------

    private void saveCurrency() {
        try {
            service.save(new CurrencyDraft(editing, txtCode.getText(), txtName.getText(), txtSymbol.getText(),
                    whole(txtDecimals, "currency.error.decimals"), checkActive.isSelected(),
                    whole(txtSort, "currency.error.sort")));
            resetCurrencyForm();
            afterWrite();
        } catch (Exception e) {
            AllAlerts.handleError(text("currency.op.save"), e);
        }
    }

    private void edit(Currency currency) {
        editing = currency.id();
        txtCode.setText(currency.code());
        txtName.setText(currency.name());
        txtSymbol.setText(currency.symbol());
        txtDecimals.setText(String.valueOf(currency.decimalPlaces()));
        txtSort.setText(String.valueOf(currency.sortOrder()));
        checkActive.setSelected(currency.active());
        editingLabel.setText(text("currency.editing", currency.label()));
        txtCode.requestFocus();
    }

    private void resetCurrencyForm() {
        editing = 0;
        txtCode.clear();
        txtName.clear();
        txtSymbol.clear();
        txtDecimals.setText("2");
        txtSort.setText("0");
        checkActive.setSelected(true);
        editingLabel.setText("");
    }

    private void makeBase(Currency currency) {
        if (currency.base()) {
            return;
        }
        try {
            if (!AllAlerts.confirm_all(text("currency.make.base"), text("currency.base.confirm", currency.label()))) {
                return;
            }
            service.setBase(currency.id());
            afterWrite();
        } catch (Exception e) {
            AllAlerts.handleError(text("currency.make.base"), e);
        }
    }

    private void toggleActive(Currency currency) {
        try {
            service.save(CurrencyDraft.switching(currency, !currency.active()));
            afterWrite();
        } catch (Exception e) {
            AllAlerts.handleError(text("currency.toggle"), e);
        }
    }

    /**
     * Deletes a currency nothing holds. One with recorded rates is refused through
     * {@code DeleteRegistry.CURRENCIES} with the count in the message - and a currency merely out of use
     * is stopped instead, which is what the active flag is for.
     */
    private void remove(Currency currency) {
        try {
            if (!AllAlerts.confirmDelete()) {
                return;
            }
            service.delete(currency.id());
            if (ratesOf != null && ratesOf.id() == currency.id()) {
                drawer.hide();
            }
            afterWrite();
        } catch (Exception e) {
            AllAlerts.handleError(text("delete"), e);
        }
    }

    // ==========================================================================================
    // One currency's rates
    // ==========================================================================================

    private void buildRatesDrawer() {
        ratesTable.setId("currencyRateHistory");
        ratesTable.setPlaceholder(new Label(text("currency.rates.empty")));
        List<RowAction<RateHistoryLine>> rateActions = RowAction.permitted(List.of(
                RowAction.of("update", AppIcon.EDIT, "app-primary-button",
                        AppPermissions.CURRENCY_RATE_UPDATE, this::editRate),
                RowAction.of("delete", AppIcon.DELETE, "app-neutral-button",
                        AppPermissions.CURRENCY_RATE_UPDATE, this::removeRate)));
        List<TableColumn<RateHistoryLine, ?>> rateColumns = new ArrayList<>();
        // Both actions ask the same key, so a reader without it has none - and an empty column of
        // buttons, seen on the read-only rendering, is width taken from the columns being read.
        if (!rateActions.isEmpty()) {
            rateColumns.add(withId("currencyRateActions", RowActionsColumn.of("currency.column.actions", rateActions)));
        }
        rateColumns.addAll(List.of(
                withId("rateDate", Columns.date("currency.rate.column.date", line -> line.rate().effectiveDate())),
                withId("rateValue", figure("currency.rate.column.rate", line -> CurrencyFormat.rate(line.rate().rate()))),
                withId("rateChange", figure("currency.column.change", line -> CurrencyFormat.change(line.changePercent()))),
                withId("rateInverse", figure("currency.column.inverse",
                        line -> CurrencyFormat.indicative(CurrencyConverter.inverse(line.rate().rate())))),
                withId("rateNotes", Columns.text("currency.rate.column.notes", line -> line.rate().notes())),
                withId("rateBy", Columns.text("currency.rate.column.by", line -> line.rate().enteredBy())),
                withId("rateAt", Columns.dateTime("currency.rate.column.at", line -> line.rate().enteredAt()))));
        ratesTable.getColumns().setAll(rateColumns);
        rateWidths.install(ratesTable);
        VBox.setVgrow(ratesTable, Priority.ALWAYS);

        rateDate.setPrefWidth(140);
        txtRate.setPrefWidth(120);
        txtNotes.setPromptText(text("currency.rate.notes"));
        txtNotes.setPrefWidth(180);
        rateCaption.getStyleClass().add("form-label");
        rateUnit.getStyleClass().add("form-label");
        btnSaveRate.setGraphic(AppIcon.SAVE.graphic());
        btnSaveRate.getStyleClass().add("app-primary-button");
        btnSaveRate.setOnAction(event -> saveRate());
        btnNewRate.setGraphic(AppIcon.ADD.graphic());
        btnNewRate.getStyleClass().add("app-neutral-button");
        btnNewRate.setOnAction(event -> resetRateForm());
        rateEditingLabel.getStyleClass().add("form-hint");
        whenEnterPressed(rateDate, txtRate, txtNotes, btnSaveRate);

        FlowPane form = new FlowPane(8, 6, caption("currency.rate.date"), rateDate, rateCaption, txtRate, rateUnit,
                txtNotes, btnSaveRate, btnNewRate, rateEditingLabel);
        form.setAlignment(Pos.CENTER_LEFT);
        // A hint, not the guard: the service asks currency.rate.update on every write.
        form.setDisable(!AuthorizationGuard.isGranted(AppPermissions.CURRENCY_RATE_UPDATE));
        Label rule = new Label(text("currency.rate.rule"));
        rule.getStyleClass().add("form-hint");
        rule.setWrapText(true);

        VBox content = new VBox(8, form, rule, ratesTable);
        drawer = RowDetailDrawer.installIn(currenciesHost);
        drawer.setContent(content);
        // 720 left the last column, when the rate was entered, behind a horizontal scroll at 1366x768.
        drawer.setPreferredWidth(820);
        drawer.setOnHidden(() -> ratesOf = null);
    }

    private void showRates(Currency currency) {
        if (currency.base()) {
            AllAlerts.alertError(text("currency.rate.error.base"));
            return;
        }
        ratesOf = currency;
        resetRateForm();
        loadRates();
        drawer.show(text("currency.rates.title", currency.label()),
                text("currency.rates.subtitle", base == null ? "" : base.label()));
    }

    private void loadRates() {
        if (ratesOf == null) {
            return;
        }
        try {
            rateCaption.setText(text("currency.rate.caption", ratesOf.code()));
            rateUnit.setText(base == null ? "" : base.code());
            ratesTable.setItems(FXCollections.observableArrayList(RateHistoryLine.of(service.rates(ratesOf.id()))));
            rateWidths.layout(ratesTable);
        } catch (Exception e) {
            AllAlerts.handleError(text("currency.rates"), e);
        }
    }

    private void saveRate() {
        if (ratesOf == null) {
            return;
        }
        try {
            service.saveRate(new ExchangeRateDraft(editingRate, ratesOf.id(), rateDate.getValue(),
                    amount(txtRate, "currency.rate.error.positive"), txtNotes.getText()));
            resetRateForm();
            afterWrite();
        } catch (Exception e) {
            AllAlerts.handleError(text("currency.op.rate"), e);
        }
    }

    private void editRate(RateHistoryLine line) {
        ExchangeRate rate = line.rate();
        editingRate = rate.id();
        rateDate.setValue(rate.effectiveDate());
        txtRate.setText(rate.rate().stripTrailingZeros().toPlainString());
        txtNotes.setText(rate.notes() == null ? "" : rate.notes());
        rateEditingLabel.setText(text("currency.rate.editing", LEFT_TO_RIGHT_MARK + rate.effectiveDate()));
        txtRate.requestFocus();
    }

    private void removeRate(RateHistoryLine line) {
        try {
            if (!AllAlerts.confirmDelete()) {
                return;
            }
            service.deleteRate(line.rate().id());
            resetRateForm();
            afterWrite();
        } catch (Exception e) {
            AllAlerts.handleError(text("delete"), e);
        }
    }

    private void resetRateForm() {
        editingRate = 0;
        rateDate.setValue(LocalDate.now());
        txtRate.clear();
        txtNotes.clear();
        rateEditingLabel.setText("");
    }

    // ==========================================================================================
    // The converter
    // ==========================================================================================

    private Tab converterTab() {
        txtAmount.setPromptText(text("currency.convert.amount"));
        txtAmount.setPrefWidth(140);
        comboFrom.setPrefWidth(220);
        comboFrom.setButtonCell(currencyCell());
        comboFrom.setCellFactory(list -> currencyCell());
        convertDate.setPrefWidth(140);
        btnConvert.setGraphic(AppIcon.EXCHANGE.graphic());
        btnConvert.getStyleClass().add("app-primary-button");
        btnConvert.setOnAction(event -> convert());
        whenEnterPressed(txtAmount, comboFrom, convertDate, btnConvert);

        FlowPane bar = new FlowPane(8, 6, caption("currency.convert.amount"), txtAmount,
                caption("currency.convert.from"), comboFrom, caption("currency.convert.date"), convertDate,
                btnConvert);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("app-card");
        bar.setPadding(new Insets(8));

        conversions.setId("currencyConversions");
        conversions.setPlaceholder(new Label(text("currency.convert.empty")));
        conversions.getColumns().setAll(List.of(
                withId("conversionCurrency", Columns.text("currency.convert.column.currency",
                        line -> line.target().label())),
                withId("conversionAmount", figure("currency.convert.column.amount", line -> line.available()
                        ? CurrencyFormat.amount(line.amount(), line.target()) + " " + line.target().symbol()
                        : text("currency.rate.none"))),
                withId("conversionRate", figure("currency.column.rate", line -> line.target().base()
                        ? "1" : line.rate() == null ? "" : CurrencyFormat.rate(line.rate().rate()))),
                withId("conversionRateDate", Columns.date("currency.column.rate.date",
                        line -> line.rate() == null ? null : line.rate().effectiveDate()))));
        conversionWidths.install(conversions);
        VBox.setVgrow(conversions, Priority.ALWAYS);

        Label note = new Label(text("currency.convert.note"));
        note.getStyleClass().add("form-hint");
        note.setWrapText(true);

        VBox content = new VBox(10, bar, note, conversions);
        content.setPadding(new Insets(10));
        Tab tab = new Tab(text("currency.tab.converter"), content);
        tab.setGraphic(AppIcon.EXCHANGE.graphic(16));
        return tab;
    }

    private void convert() {
        Currency from = comboFrom.getValue();
        if (from == null) {
            AllAlerts.alertError(text("currency.convert.error.currency"));
            return;
        }
        try {
            BigDecimal amount = amount(txtAmount, "currency.convert.error.amount");
            LocalDate day = convertDate.getValue() == null ? LocalDate.now() : convertDate.getValue();
            conversions.setItems(FXCollections.observableArrayList(service.convert(amount, from.id(), day)));
            conversionWidths.layout(conversions);
        } catch (Exception e) {
            AllAlerts.handleError(text("currency.tab.converter"), e);
        }
    }

    // ==========================================================================================
    // Loading
    // ==========================================================================================

    private void reload() {
        try {
            List<Currency> all = service.all();
            base = service.base();
            ratesToday = service.ratesInForce(LocalDate.now());
            table.setItems(FXCollections.observableArrayList(all));
            widths.layout(table);
            baseLabel.setText(text("currency.base.current", base.label()));

            Currency chosen = comboFrom.getValue();
            List<Currency> active = all.stream().filter(Currency::active).toList();
            comboFrom.setItems(FXCollections.observableArrayList(active));
            comboFrom.getSelectionModel().select(active.stream()
                    .filter(currency -> chosen == null ? currency.base() : currency.id() == chosen.id())
                    .findFirst().orElse(null));

            // The drawer follows what it shows: a currency deleted elsewhere closes it, a rate recorded
            // elsewhere appears in it.
            if (ratesOf != null) {
                int shown = ratesOf.id();
                ratesOf = all.stream().filter(currency -> currency.id() == shown).findFirst().orElse(null);
                if (ratesOf == null || ratesOf.base()) {
                    drawer.hide();
                } else {
                    loadRates();
                }
            }
        } catch (Exception e) {
            AllAlerts.handleError(text("currency.title"), e);
        }
    }

    private void afterWrite() {
        if (eventBus != null) {
            // Reloads this screen through its own subscription, and reaches the other tills through the relay.
            eventBus.publish(new CurrenciesChanged());
        } else {
            reload();
        }
    }

    // ==========================================================================================
    // Plumbing
    // ==========================================================================================

    /** A whole number typed in a field - Arabic digits included - or the refusal named. */
    private static int whole(TextField field, String refusalKey) throws UserValidationException {
        BigDecimal value = amount(field, refusalKey);
        try {
            return value.intValueExact();
        } catch (ArithmeticException e) {
            throw new UserValidationException(refusalKey, e);
        }
    }

    /**
     * A number typed in a field, read the way the other screens read one: separators dropped, ٠-٩ as
     * digits. Blank or not a number is the refusal named, never a zero.
     */
    private static BigDecimal amount(TextField field, String refusalKey) throws UserValidationException {
        try {
            BigDecimal value = NumberTextConverter.parse(field.getText());
            if (value == null) {
                throw new UserValidationException(refusalKey);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new UserValidationException(refusalKey, e);
        }
    }

    /** A column of figures written as text - a rate or a change - aligned as every figure column is. */
    private static <S> TableColumn<S, String> figure(String titleKey, Function<S, String> extractor) {
        TableColumn<S, String> column = Columns.text(titleKey, extractor);
        column.setStyle(Columns.AMOUNT_ALIGNMENT);
        return column;
    }

    private static ListCell<Currency> currencyCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Currency currency, boolean empty) {
                super.updateItem(currency, empty);
                setText(empty || currency == null ? null : currency.label());
            }
        };
    }

    private static void anchor(Node node) {
        AnchorPane.setTopAnchor(node, 0.0);
        AnchorPane.setBottomAnchor(node, 0.0);
        AnchorPane.setLeftAnchor(node, 0.0);
        AnchorPane.setRightAnchor(node, 0.0);
    }

    private static Label caption(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    private static <S, C> TableColumn<S, C> withId(String id, TableColumn<S, C> column) {
        column.setId(id);
        return column;
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }

    private static String text(String key, Object... args) {
        return LanguageManager.getInstance().getString(key, args);
    }
}
