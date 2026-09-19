package com.hamza.account.controller.employee;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.delegate.DiscountCeilingService;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.delegate.CommissionBasis;
import com.hamza.account.features.delegate.CommissionRule;
import com.hamza.account.features.delegate.CommissionRuleForm;
import com.hamza.account.features.delegate.CommissionRuleService;
import com.hamza.account.features.delegate.CommissionTiers;
import com.hamza.account.features.delegate.TierMode;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.DoubleSetting;
import com.hamza.controlsfx.table.Columns;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.hamza.controlsfx.others.DateSetting.dateAction;
import static com.hamza.controlsfx.others.Utils.setOptionalNumberFormatter;
import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * The commission rules of one delegate: every rule he has had, and the form that records the
 * next one.
 * <p>
 * It is {@link EmployeeSalaryController} for a second dated figure, on purpose - a target and its
 * rates are decided the way a salary is, ahead of the month they apply to, and a rule dated in
 * the future is the ordinary case.
 * <p>
 * Two things on it are there because a rule is easy to misread and expensive to get wrong. The
 * line under the date says which month a rule first governs - the one whose <b>first day</b> it
 * was in force on - and the try-out box computes a commission from the form as it stands, with
 * the same {@link CommissionTiers} the monthly run will use, so what "from 80% at 2%" pays is
 * seen before it is saved rather than discovered on the first pay day.
 */
@Log4j2
@FxmlPath(pathFile = "commission-rules.fxml")
public class CommissionRulesController implements AddInterface {

    private final CommissionRuleService ruleService = ServiceRegistry.get(CommissionRuleService.class);
    private final int employeeId;
    private final String employeeName;

    private final TableView<CommissionRule> table = new TableView<>();
    private final ContentSizedColumns<CommissionRule> columnSizing = new ContentSizedColumns<>();
    private final DatePicker effectiveFrom = new DatePicker();
    private final ComboBox<CommissionBasis> comboBasis = new ComboBox<>();
    private final ComboBox<TierMode> comboMode = new ComboBox<>();
    private final TextField txtTarget = new TextField();
    private final TextField[] tierFrom = {new TextField(), new TextField(), new TextField()};
    private final TextField[] tierRate = {new TextField(), new TextField(), new TextField()};
    private final TextField txtNotes = new TextField();
    private final TextField txtTryAmount = new TextField();
    private final Label tryResult = new Label();
    private final TextField txtCeiling = new TextField();
    private final DiscountCeilingService ceilingService = new DiscountCeilingService();

    @FXML
    private VBox box;
    @FXML
    private StackPane stackPane;

    public CommissionRulesController(int employeeId, String employeeName) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
    }

    @FXML
    public void initialize() {
        otherSetting();
        selectData();
    }

    @Override
    public void otherSetting() {
        stackPane.getStyleClass().add("screen-employees");

        comboBasis.getItems().setAll(CommissionBasis.values());
        comboBasis.setConverter(converter(basis -> basis == null ? "" : text(basis.messageKey())));
        comboMode.getItems().setAll(TierMode.values());
        comboMode.setConverter(converter(mode -> mode == null ? "" : text(mode.messageKey())));

        dateAction(effectiveFrom);
        // The target and the first tier are entry boxes and start at zero. The second and third
        // tiers are optional: an untouched box there must stay empty, because an empty pair is
        // "no such tier" while 0 and 0 is a tier starting at zero - which a seeding formatter
        // would make of every rule, and which is refused for having no order.
        setTextFormatter(txtTarget, tierFrom[0], tierRate[0]);
        setOptionalNumberFormatter(tierFrom[1], tierRate[1], tierFrom[2], tierRate[2], txtTryAmount);
        for (int i = 0; i < tierFrom.length; i++) {
            tierFrom[i].setPrefColumnCount(5);
            tierRate[i].setPrefColumnCount(5);
        }
        txtNotes.setPromptText(text("column.notes"));
        HBox.setHgrow(txtNotes, Priority.ALWAYS);
        txtTryAmount.setPromptText(text("commission.try.amount"));
        whenEnterPressed(txtTarget, tierFrom[0], tierRate[0], tierFrom[1], tierRate[1],
                tierFrom[2], tierRate[2], txtNotes);

        List<javafx.beans.Observable> inputs = new java.util.ArrayList<>(List.of(
                txtTarget.textProperty(), txtTryAmount.textProperty(), comboMode.valueProperty()));
        for (int i = 0; i < tierFrom.length; i++) {
            inputs.add(tierFrom[i].textProperty());
            inputs.add(tierRate[i].textProperty());
        }
        inputs.forEach(input -> input.addListener(observable -> showTryOut()));

        buildTable();
        resetData();
        box.getChildren().setAll(header(), entryCard(), ceilingCard(), table);
        VBox.setVgrow(table, Priority.ALWAYS);
    }

    private HBox header() {
        Label title = new Label(text("commission.rules.title") + " - " + employeeName);
        title.getStyleClass().add("party-screen-title");
        // His approved months, beside the rules that produced them. A plain button: it names no
        // row, it names the delegate this whole screen is about.
        javafx.scene.control.Button statement = new javafx.scene.control.Button(
                text("commission.statement.title"), AppIcon.REPORT.graphic());
        statement.getStyleClass().add("app-neutral-button");
        statement.setOnAction(event -> openStatement());
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(12, AppIcon.REPORT.graphic(24), title, spacer, statement);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("party-screen-header");
        return bar;
    }

    private void openStatement() {
        try {
            new com.hamza.account.view.OpenApplication<>(
                    new CommissionStatementController(employeeId, employeeName));
        } catch (Exception e) {
            AllAlerts.handleError(text("commission.statement.title"), e);
        }
    }

    private VBox entryCard() {
        HBox first = row(caption("commission.rule.effective.from"), effectiveFrom,
                caption("commission.rule.basis"), comboBasis,
                caption("commission.rule.target"), txtTarget,
                caption("commission.rule.mode"), comboMode);
        Label monthHint = new Label(text("commission.rule.month.hint"));
        monthHint.getStyleClass().add("form-hint");
        monthHint.setWrapText(true);

        HBox tiers = row();
        for (int i = 0; i < tierFrom.length; i++) {
            // Each caption stays in one HBox with the box it names: a row that wraps between a
            // caption and its control reads as though the caption labelled the control before it.
            tiers.getChildren().add(row(captionText(text("commission.rule.tier") + " " + (i + 1)),
                    caption("commission.rule.tier.from"), tierFrom[i],
                    caption("commission.rule.tier.rate"), tierRate[i]));
        }
        Label tierHint = new Label(text("commission.rule.tier.hint"));
        tierHint.getStyleClass().add("form-hint");
        tierHint.setWrapText(true);

        tryResult.getStyleClass().add("form-label");
        HBox tryOut = row(caption("commission.try.caption"), txtTryAmount, tryResult);

        VBox card = new VBox(8, first, monthHint, tiers, tierHint, row(txtNotes), tryOut);
        card.getStyleClass().addAll("app-card", "party-form-card");
        return card;
    }

    /**
     * The delegate's discount ceiling. It is not part of a rule - it has no date, and it judges
     * an invoice rather than a month - so it has a box and a button of its own, and the dialog's
     * save leaves it alone. An empty box is "no ceiling"; zero is a delegate who may discount
     * nothing, which is why the box takes the optional formatter and not the seeding one.
     */
    private VBox ceilingCard() {
        setOptionalNumberFormatter(txtCeiling);
        txtCeiling.setPrefColumnCount(5);
        javafx.scene.control.Button save = new javafx.scene.control.Button(
                text("delegate.ceiling.save"), AppIcon.SAVE.graphic());
        save.getStyleClass().add("app-neutral-button");
        save.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        save.setOnAction(event -> saveCeiling());
        boolean mayDecide = AuthorizationGuard.isGranted(AppPermissions.COMMISSION_RULE_UPDATE);
        txtCeiling.setDisable(!mayDecide);
        save.setDisable(!mayDecide);

        Label hint = new Label(text("delegate.ceiling.hint"));
        hint.getStyleClass().add("form-hint");
        hint.setWrapText(true);
        VBox card = new VBox(8, row(caption("delegate.ceiling.caption"), txtCeiling, save), hint);
        card.getStyleClass().addAll("app-card", "party-form-card");
        showCeiling();
        return card;
    }

    private void showCeiling() {
        try {
            txtCeiling.setText(ceilingService.ceilingOf(employeeId)
                    .map(ceiling -> plain(ceiling.maxPercent())).orElse(""));
        } catch (Exception e) {
            AllAlerts.handleError(text("commission.error.operation"), e);
        }
    }

    private void saveCeiling() {
        try {
            ceilingService.update(employeeId, optional(txtCeiling));
            showCeiling();
            AllAlerts.alertSave();
        } catch (Exception e) {
            AllAlerts.handleError(text("commission.error.operation"), e);
        }
    }

    private void buildTable() {
        table.setId("commission-rules-history");
        table.setPlaceholder(new Label(text("commission.rules.empty")));
        table.getColumns().setAll(List.of(
                RowActionsColumn.of("employee.column.actions", RowAction.permitted(List.of(
                        RowAction.of("commission.action.use", AppIcon.EDIT, "app-primary-button",
                                AppPermissions.COMMISSION_RULE_UPDATE, this::loadIntoForm),
                        RowAction.of("delete", AppIcon.DELETE, "app-neutral-button",
                                AppPermissions.COMMISSION_RULE_UPDATE, this::remove)))),
                Columns.date("commission.rule.effective.from", CommissionRule::effectiveFrom),
                Columns.text("commission.rule.basis", rule -> text(rule.basis().messageKey())),
                Columns.money("commission.rule.target", CommissionRule::target),
                Columns.text("commission.rule.mode", rule -> text(rule.tierMode().messageKey())),
                Columns.text("commission.rule.tiers", CommissionRulesController::describe),
                Columns.text("column.notes", CommissionRule::notes)));
        columnSizing.install(table);
    }

    /** "80% → 1% | 100% → 2%": numbers only, so it reads the same in either language. */
    private static String describe(CommissionRule rule) {
        return rule.tiers().tiers().stream()
                .map(tier -> tier.fromPercent().stripTrailingZeros().toPlainString() + "% → "
                        + tier.ratePercent().stripTrailingZeros().toPlainString() + "%")
                .collect(Collectors.joining("  |  "));
    }

    @Override
    public int insertData() throws Exception {
        ruleService.save(employeeId, effectiveFrom.getValue(), comboBasis.getValue(), comboMode.getValue(),
                amount(txtTarget), CommissionRuleForm.tiers(tierValues()), blankToNull(txtNotes.getText()));
        // The dialog stays open, and the contract is "exactly 1 means saved".
        selectData();
        return 1;
    }

    /** Nothing else shows a commission rule yet, so there is nobody to tell. */
    @Override
    public void afterSaved() {
    }

    @Override
    public void selectData() {
        try {
            table.setItems(FXCollections.observableArrayList(ruleService.history(employeeId)));
            columnSizing.layout(table);
        } catch (Exception e) {
            AllAlerts.handleError(text("commission.error.operation"), e);
        }
    }

    /** Copies a rule into the form, so the next one is an amendment of it rather than typed from nothing. */
    private void loadIntoForm(CommissionRule rule) {
        effectiveFrom.setValue(rule.effectiveFrom());
        comboBasis.getSelectionModel().select(rule.basis());
        comboMode.getSelectionModel().select(rule.tierMode());
        txtTarget.setText(plain(rule.target()));
        List<CommissionTiers.Tier> tiers = rule.tiers().tiers();
        for (int i = 0; i < tierFrom.length; i++) {
            tierFrom[i].setText(i < tiers.size() ? plain(tiers.get(i).fromPercent()) : "");
            tierRate[i].setText(i < tiers.size() ? plain(tiers.get(i).ratePercent()) : "");
        }
        txtNotes.setText(rule.notes() == null ? "" : rule.notes());
        showTryOut();
    }

    private void remove(CommissionRule rule) {
        try {
            if (!AllAlerts.confirmDelete()) {
                return;
            }
            ruleService.remove(employeeId, rule.id());
            selectData();
        } catch (Exception e) {
            AllAlerts.handleError(text("commission.error.operation"), e);
        }
    }

    @Override
    public void resetData() {
        // The first of this month: a month is judged by the rule in force on its first day, so
        // this is the date that makes the rule govern the month the user is looking at.
        effectiveFrom.setValue(LocalDate.now().withDayOfMonth(1));
        comboBasis.getSelectionModel().select(CommissionBasis.SALES);
        comboMode.getSelectionModel().select(TierMode.WHOLE);
        txtTarget.setText("0");
        tierFrom[0].setText("0");
        tierRate[0].setText("0");
        for (int i = 1; i < tierFrom.length; i++) {
            tierFrom[i].clear();
            tierRate[i].clear();
        }
        txtNotes.clear();
        showTryOut();
    }

    /**
     * What the form as it stands would pay on the try-out amount. Computed from held values,
     * never read back from a label; a form that is not yet a valid rule shows nothing rather
     * than a refusal for every keystroke.
     */
    private void showTryOut() {
        BigDecimal amount = optional(txtTryAmount);
        if (amount == null) {
            tryResult.setText("");
            return;
        }
        try {
            CommissionTiers.Result result = new CommissionTiers(CommissionRuleForm.tiers(tierValues()))
                    .calculate(amount, amount(txtTarget), comboMode.getValue() == null ? TierMode.WHOLE : comboMode.getValue());
            tryResult.setText(result.tier() == 0
                    ? text("commission.try.none")
                    : text("commission.try.result") + " " + Columns.money(result.amount()));
        } catch (Exception notARuleYet) {
            tryResult.setText("");
        }
    }

    private BigDecimal[] tierValues() {
        BigDecimal[] values = new BigDecimal[tierFrom.length * 2];
        for (int i = 0; i < tierFrom.length; i++) {
            values[i * 2] = optional(tierFrom[i]);
            values[i * 2 + 1] = optional(tierRate[i]);
        }
        return values;
    }

    @Override
    public @org.jetbrains.annotations.NotNull BooleanBinding checkDataToEnableButton() {
        return Bindings.createBooleanBinding(
                () -> effectiveFrom.getValue() == null || comboBasis.getValue() == null
                        || comboMode.getValue() == null,
                effectiveFrom.valueProperty(), comboBasis.valueProperty(), comboMode.valueProperty());
    }

    @Override
    public boolean keepDialogOpenAfterSave() {
        return true;
    }

    @Override
    public boolean resize() {
        return true;
    }

    @Override
    public String dialogStyleClass() {
        return "screen-employees";
    }

    private static HBox row(javafx.scene.Node... nodes) {
        HBox row = new HBox(8, nodes);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Label caption(String key) {
        return captionText(text(key));
    }

    private static Label captionText(String value) {
        Label label = new Label(value);
        label.getStyleClass().add("form-label");
        label.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        return label;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** An entry box: empty is zero. */
    private static BigDecimal amount(TextField field) {
        BigDecimal value = optional(field);
        return value == null ? BigDecimal.ZERO : value;
    }

    /** An optional box: empty is absent, which is not the same thing as zero. */
    private static BigDecimal optional(TextField field) {
        String value = field.getText();
        return value == null || value.isBlank()
                ? null
                : BigDecimal.valueOf(DoubleSetting.parseDoubleOrDefault(value));
    }

    private static <T> StringConverter<T> converter(Function<T, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return label.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        };
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
