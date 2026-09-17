package com.hamza.account.controller.expense;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.ExpensesChanged;
import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.account.features.expense.ExpenseHeadingDraft;
import com.hamza.account.features.expense.ExpenseHeadingService;
import com.hamza.account.features.expense.ExpenseHeadingUsage;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.table.Columns;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * The headings expenses are filed under.
 * <p>
 * <b>There was no screen at all, and could not be.</b> The headings were {@code ExpensesType} - six
 * constants with ids matched to six rows by hand - so a shop could not add "صيانة" or rename "أخرى",
 * and the wallet-fee heading V21 seeded was on no screen (docs/expenses-plan.md ع-١, ع-٢).
 * <p>
 * The list is the tree in table form: a main heading, the headings under it, the next main heading.
 * Beside each, how many expenses it holds and what they came to this year - the two things somebody
 * deciding to stop or delete a heading needs to see first. A stopped heading is marked, not hidden: this
 * is the one screen it is restarted from, and hiding it here would make stopping a one-way door.
 * <p>
 * What may be done to a heading is {@code ExpenseHeadingRules}'s to say, and the service asks it; a
 * heading the system depends on is marked here so the refusal is not a surprise.
 */
@Log4j2
@FxmlPath(pathFile = "expense-headings.fxml")
public class ExpenseHeadingsController implements AddInterface {

    private static final PseudoClass STOPPED = PseudoClass.getPseudoClass("stopped");
    private static final PseudoClass SUB = PseudoClass.getPseudoClass("sub-heading");

    private final ExpenseHeadingService headingService = ServiceRegistry.get(ExpenseHeadingService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);

    private final TableView<ExpenseHeading> table = new TableView<>();
    private final ContentSizedColumns<ExpenseHeading> columnSizing = new ContentSizedColumns<>();
    private final TextField txtName = new TextField();
    private final ComboBox<ExpenseHeading> comboParent = new ComboBox<>();
    private final CheckBox isEmployeePayment = new CheckBox(text("expense.heading.employee"));
    private final CheckBox isActive = new CheckBox(text("expense.heading.active"));
    private final Label editingLabel = new Label();

    @FXML
    private VBox box;
    @FXML
    private StackPane stackPane;

    private Map<Integer, ExpenseHeadingUsage> usage = Map.of();

    /** Which heading the entry bar is editing; 0 while it is adding one. */
    private int editing;

    @FXML
    public void initialize() {
        otherSetting();
        selectData();
    }

    @Override
    public void otherSetting() {
        stackPane.getStyleClass().add("screen-expenses");

        txtName.setPromptText(text("expense.heading.name"));
        txtName.setPrefWidth(220);
        comboParent.setConverter(converter());
        comboParent.setPrefWidth(200);
        isActive.setSelected(true);
        editingLabel.getStyleClass().add("form-hint");
        HBox.setHgrow(editingLabel, Priority.ALWAYS);
        whenEnterPressed(txtName, comboParent);

        buildTable();
        box.getChildren().setAll(header(), entryBar(), table);
        VBox.setVgrow(table, Priority.ALWAYS);
        // Opened at 1366x768 the dialog was 701 points tall, its buttons at the bottom edge of a screen
        // that has a taskbar under it. The table grows when the dialog is enlarged.
        table.setPrefHeight(320);
    }

    private HBox header() {
        Label title = new Label(text("expense.headings.title"));
        title.getStyleClass().add("party-screen-title");
        Label subtitle = new Label(text("expense.headings.subtitle"));
        subtitle.getStyleClass().add("party-screen-subtitle");
        subtitle.setWrapText(true);
        VBox captions = new VBox(3, title, subtitle);
        HBox bar = new HBox(12, AppIcon.TREE.graphic(24), captions);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("party-screen-header");
        return bar;
    }

    private VBox entryBar() {
        HBox row = new HBox(8, caption("expense.heading.name"), txtName, caption("expense.heading.parent"),
                comboParent, isEmployeePayment, isActive, editingLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox bar = new VBox(8, row);
        bar.getStyleClass().addAll("app-card", "party-form-card");
        return bar;
    }

    private void buildTable() {
        table.setId("expense-headings-table");
        table.setPlaceholder(new Label(text("expense.headings.empty")));
        List<RowAction<ExpenseHeading>> actions = RowAction.permitted(List.of(
                RowAction.of("update", AppIcon.EDIT, "app-primary-button",
                        AppPermissions.EXPENSES_HEADINGS_UPDATE, this::edit),
                RowAction.of("expense.heading.add.sub", AppIcon.ADD, "app-neutral-button",
                        AppPermissions.EXPENSES_HEADINGS_UPDATE, this::addUnder),
                RowAction.of("expense.heading.toggle", AppIcon.SECURITY, "app-neutral-button",
                        AppPermissions.EXPENSES_HEADINGS_UPDATE, this::toggleActive),
                RowAction.of("delete", AppIcon.DELETE, "app-neutral-button",
                        AppPermissions.EXPENSES_HEADINGS_UPDATE, this::remove)));
        table.getColumns().setAll(List.of(
                RowActionsColumn.of("employee.column.actions", actions),
                Columns.number("code", ExpenseHeading::id),
                Columns.text("expense.heading.name", this::indentedName),
                Columns.text("expense.heading.parent", heading -> heading.parentName() == null ? "" : heading.parentName()),
                Columns.text("expense.heading.employee", heading -> text(heading.employeePayment() ? "yes" : "no")),
                Columns.text("expense.heading.active", heading -> text(heading.active() ? "yes" : "no")),
                Columns.text("expense.heading.system", heading -> heading.isSystem() ? text("yes") : "")));
        // The two figures only for a reader who may see expenses: a zero shown to anybody else would be a
        // statement that nothing was spent rather than a refusal to say.
        if (AuthorizationGuard.isGranted(AppPermissions.EXPENSES_SHOW)) {
            table.getColumns().add(Columns.number("expense.heading.count", heading -> usageOf(heading).expenseCount()));
            table.getColumns().add(Columns.money("expense.heading.year.total", heading -> usageOf(heading).totalSince()));
        }
        table.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(ExpenseHeading heading, boolean empty) {
                super.updateItem(heading, empty);
                pseudoClassStateChanged(STOPPED, !empty && heading != null && !heading.active());
                pseudoClassStateChanged(SUB, !empty && heading != null && !heading.isMain());
            }
        });
        columnSizing.install(table);
    }

    /** A sub-heading is set in under its main one, so the table reads as the tree it is. */
    private String indentedName(ExpenseHeading heading) {
        return heading.isMain() ? heading.name() : "    " + heading.name();
    }

    private ExpenseHeadingUsage usageOf(ExpenseHeading heading) {
        return usage.getOrDefault(heading.id(), ExpenseHeadingUsage.none(heading.id()));
    }

    @Override
    public void selectData() {
        try {
            List<ExpenseHeading> headings = headingService.all();
            usage = AuthorizationGuard.isGranted(AppPermissions.EXPENSES_SHOW)
                    ? headingService.usage(LocalDate.now().withDayOfYear(1)) : Map.of();
            table.setItems(FXCollections.observableArrayList(headings));
            columnSizing.layout(table);

            List<ExpenseHeading> parents = new ArrayList<>();
            parents.add(noParent());
            headings.stream().filter(ExpenseHeading::isMain).forEach(parents::add);
            ExpenseHeading chosen = comboParent.getValue();
            comboParent.setItems(FXCollections.observableArrayList(parents));
            selectParent(chosen == null ? null : chosen.id());
        } catch (Exception e) {
            report(e);
        }
    }

    /** Must answer exactly 1, or the dialog treats a saved heading as a failure and stays open. */
    @Override
    public int insertData() throws Exception {
        ExpenseHeading parent = comboParent.getValue();
        headingService.save(new ExpenseHeadingDraft(editing, txtName.getText(),
                parent == null || parent.id() == 0 ? null : parent.id(), isActive.isSelected(),
                isEmployeePayment.isSelected()));
        editing = 0;
        selectData();
        return 1;
    }

    @Override
    public void afterSaved() {
        resetData();
        if (eventBus != null) {
            // The expenses list shows the heading on every row and filters by it.
            eventBus.publish(new ExpensesChanged());
        }
    }

    private void edit(ExpenseHeading heading) {
        editing = heading.id();
        txtName.setText(heading.name());
        selectParent(heading.parentId());
        isEmployeePayment.setSelected(heading.employeePayment());
        isActive.setSelected(heading.active());
        editingLabel.setText(LanguageManager.getInstance().getString("expense.heading.editing", heading.path()));
        txtName.requestFocus();
    }

    /** Starts a new heading under this one - or under this one's own main heading, for a sub-heading. */
    private void addUnder(ExpenseHeading heading) {
        resetData();
        selectParent(heading.isMain() ? heading.id() : heading.parentId());
        txtName.requestFocus();
    }

    private void toggleActive(ExpenseHeading heading) {
        try {
            headingService.save(new ExpenseHeadingDraft(heading.id(), heading.name(), heading.parentId(),
                    !heading.active(), heading.employeePayment()));
            selectData();
            afterSaved();
        } catch (Exception e) {
            report(e);
        }
    }

    /**
     * Deletes a heading nothing holds. One with expenses or sub-headings is refused through
     * {@code DeleteRegistry.EXPENSE_HEADINGS} with the count in the message - and a heading merely out of
     * use is stopped instead, which is what the active flag is for.
     */
    private void remove(ExpenseHeading heading) {
        try {
            if (!AllAlerts.confirmDelete()) {
                return;
            }
            headingService.delete(heading.id());
            selectData();
            afterSaved();
        } catch (Exception e) {
            report(e);
        }
    }

    private void selectParent(Integer parentId) {
        comboParent.getItems().stream()
                .filter(heading -> parentId == null ? heading.id() == 0 : heading.id() == parentId)
                .findFirst()
                .ifPresentOrElse(comboParent.getSelectionModel()::select,
                        () -> comboParent.getSelectionModel().selectFirst());
    }

    @Override
    public void resetData() {
        editing = 0;
        txtName.clear();
        selectParent(null);
        isEmployeePayment.setSelected(false);
        isActive.setSelected(true);
        editingLabel.setText("");
    }

    @Override
    public @NotNull BooleanBinding checkDataToEnableButton() {
        return Bindings.createBooleanBinding(() -> txtName.getText() == null || txtName.getText().isBlank(),
                txtName.textProperty());
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
        return "screen-expenses";
    }

    private static ExpenseHeading noParent() {
        return new ExpenseHeading(0, "", null, null, true, null, false);
    }

    private static StringConverter<ExpenseHeading> converter() {
        return new StringConverter<>() {
            @Override
            public String toString(ExpenseHeading heading) {
                return heading == null || heading.id() == 0 ? text("expense.heading.no.parent") : heading.name();
            }

            @Override
            public ExpenseHeading fromString(String text) {
                return null;
            }
        };
    }

    private void report(Exception e) {
        AllAlerts.handleError(text("expense.error.operation"), e);
    }

    private static Label caption(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        return label;
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }

}
