package com.hamza.account.controller.expense;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.account.features.expense.ExpenseHeadingService;
import com.hamza.account.features.expense.ExpenseService;
import com.hamza.account.features.expense.recurring.ExpenseFrequency;
import com.hamza.account.features.expense.recurring.ExpenseRecurring;
import com.hamza.account.features.expense.recurring.ExpenseRecurringDraft;
import com.hamza.account.features.expense.recurring.ExpenseRecurringDue;
import com.hamza.account.features.expense.recurring.ExpenseRecurringService;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hamza.controlsfx.others.DateSetting.dateAction;
import static com.hamza.controlsfx.others.DateSetting.dateFilter;
import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * The expenses the shop pays every month, quarter or year - the rent, a subscription, a licence
 * (docs/expenses-plan.md §5.2).
 * <p>
 * <b>Nothing on this screen records an expense.</b> A template reminds, and "record now" opens the entry
 * screen filled in from it; the person holding the drawer saves it, through {@code ExpenseService},
 * with the same permission, period lock, shift gate and cash journal as any other expense. Recording
 * from a template - here or from a scheduled task - would be a second writer of {@code expenses_details}
 * and would need an open shift belonging to somebody, which a timer has no way to be.
 * <p>
 * <b>A template that has recorded expenses is stopped, not deleted.</b> Those rows carry its id, and the
 * key is {@code ON DELETE SET NULL}: deleting it would quietly cut the only thing that says where that
 * money came from - and that link is also what "already recorded" is measured by, so the reminder for a
 * period already paid would come back. The service refuses it and the count is on the row.
 * <p>
 * The top half is what is due today, soonest first; the bottom half is every template, stopped ones
 * included - this is the screen a stopped one is restarted from.
 */
@Log4j2
@FxmlPath(pathFile = "expense-recurring.fxml")
public class ExpenseRecurringController implements AddInterface {

    private static final PseudoClass STOPPED = PseudoClass.getPseudoClass("stopped");
    private static final PseudoClass LATE = PseudoClass.getPseudoClass("report-total");

    private final ExpenseRecurringService recurringService = ServiceRegistry.get(ExpenseRecurringService.class);
    private final ExpenseHeadingService headingService = ServiceRegistry.get(ExpenseHeadingService.class);
    private final ExpenseService expenseService = ServiceRegistry.get(ExpenseService.class);

    private final TableView<ExpenseRecurringDue> dueTable = new TableView<>();
    private final TableView<ExpenseRecurring> table = new TableView<>();
    private final ContentSizedColumns<ExpenseRecurringDue> dueSizing = new ContentSizedColumns<>();
    private final ContentSizedColumns<ExpenseRecurring> columnSizing = new ContentSizedColumns<>();

    private final ComboBox<ExpenseHeading> comboHeading = new ComboBox<>();
    private final ComboBox<TreasuryBalanceSummary> comboTreasury = new ComboBox<>();
    private final TextField txtAmount = new TextField();
    private final ComboBox<ExpenseFrequency> comboFrequency = new ComboBox<>();
    private final Spinner<Integer> spinnerDay = new Spinner<>(1, 31, 1);
    private final DatePicker startDate = new DatePicker(LocalDate.now().withDayOfMonth(1));
    private final DatePicker endDate = new DatePicker();
    private final TextField txtPayee = new TextField();
    private final TextField txtNotes = new TextField();
    private final CheckBox isActive = new CheckBox(text("expense.heading.active"));
    private final Label editingLabel = new Label();

    /** How many expenses each template has recorded, for the column and for the delete refusal. */
    private final Map<Integer, Integer> recorded = new HashMap<>();

    /** Opens the expense entry screen filled in from a template; the caller decides how. */
    private final Consumer<ExpenseRecurringDue> record;

    @FXML
    private VBox box;
    @FXML
    private StackPane stackPane;

    /** Which template the form is correcting; 0 while it is adding one. */
    private int editing;

    public ExpenseRecurringController() {
        this(null);
    }

    /**
     * @param record what "record now" does, or {@code null} to open the ordinary entry screen from here
     */
    public ExpenseRecurringController(Consumer<ExpenseRecurringDue> record) {
        this.record = record == null ? this::recordNow : record;
    }

    @FXML
    public void initialize() {
        otherSetting();
        selectData();
    }

    @Override
    public void otherSetting() {
        stackPane.getStyleClass().add("screen-expenses");

        comboHeading.setConverter(converter(heading -> heading == null ? "" : heading.path()));
        comboHeading.setPrefWidth(220);
        comboTreasury.setConverter(converter(treasury -> treasury == null ? "" : treasury.name()));
        comboTreasury.setPrefWidth(180);
        comboFrequency.setConverter(converter(frequency -> frequency == null ? "" : text(frequency.messageKey())));
        comboFrequency.setItems(FXCollections.observableArrayList(ExpenseFrequency.values()));
        comboFrequency.getSelectionModel().selectFirst();
        comboFrequency.setPrefWidth(140);
        spinnerDay.setPrefWidth(90);
        spinnerDay.setEditable(true);
        setTextFormatter(txtAmount);
        txtAmount.setPrefWidth(140);
        dateAction(startDate);
        // Not dateAction: it seeds today, and an end date of today means a template that can never
        // fall due again. An open end is the ordinary case, so the field starts empty - the same
        // distinction the party screens draw between an entry field and one whose blank means "none".
        dateFilter(endDate);
        startDate.setPrefWidth(150);
        endDate.setPrefWidth(150);
        endDate.setPromptText(text("expense.recurring.open.end"));
        txtPayee.setPromptText(text("expense.column.payee"));
        txtPayee.setPrefWidth(160);
        txtNotes.setPromptText(text("column.notes"));
        txtNotes.setPrefWidth(180);
        isActive.setSelected(true);
        editingLabel.getStyleClass().add("form-hint");
        HBox.setHgrow(editingLabel, Priority.ALWAYS);
        whenEnterPressed(comboHeading, comboTreasury, txtAmount, txtPayee, txtNotes);

        buildDueTable();
        buildTable();
        box.getChildren().setAll(header(), dueSection(), entryBar(), table);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setPrefHeight(240);
    }

    private HBox header() {
        Label title = new Label(text("expense.recurring.title"));
        title.getStyleClass().add("party-screen-title");
        Label subtitle = new Label(text("expense.recurring.subtitle"));
        subtitle.getStyleClass().add("party-screen-subtitle");
        subtitle.setWrapText(true);
        VBox captions = new VBox(3, title, subtitle);
        HBox bar = new HBox(12, AppIcon.REFRESH.graphic(24), captions);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("party-screen-header");
        return bar;
    }

    private VBox dueSection() {
        Label caption = new Label(text("expense.recurring.due.title"));
        caption.getStyleClass().add("party-screen-subtitle");
        VBox section = new VBox(6, caption, dueTable);
        section.getStyleClass().addAll("app-card", "party-form-card");
        section.setId("expense-recurring-due");
        return section;
    }

    /**
     * Nine controls, so a {@code FlowPane}: an {@code HBox} of this many pushed the trend tab's table off
     * a 1366-point screen, and a dialog has less room than that window did.
     */
    private VBox entryBar() {
        FlowPane row = new FlowPane(8, 8,
                field("expense.column.heading", comboHeading),
                field("invoice.treasury", comboTreasury),
                field("column.amount", txtAmount),
                field("expense.recurring.frequency", comboFrequency),
                field("expense.recurring.day", spinnerDay),
                field("expense.recurring.start", startDate),
                field("expense.recurring.end", endDate),
                txtPayee, txtNotes, isActive, editingLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox bar = new VBox(8, row);
        bar.getStyleClass().addAll("app-card", "party-form-card");
        bar.setId("expense-recurring-form");
        return bar;
    }

    private void buildDueTable() {
        dueTable.setId("expense-recurring-due-table");
        dueTable.setPlaceholder(new Label(text("expense.recurring.due.empty")));
        dueTable.setPrefHeight(150);
        dueTable.getColumns().setAll(List.of(
                RowActionsColumn.of("employee.column.actions", RowAction.permitted(List.of(
                        RowAction.of("expense.recurring.record", AppIcon.ADD, "app-primary-button",
                                AppPermissions.EXPENSES_CREATE, record::accept)))),
                Columns.text("expense.column.heading", due -> due.template().headingPath()),
                Columns.money("column.amount", due -> due.template().amount()),
                Columns.text("expense.recurring.due.on", due -> due.dueOn().toString()),
                Columns.text("expense.recurring.late", this::lateness),
                Columns.text("expense.column.payee", due -> due.template().payee())));
        dueTable.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(ExpenseRecurringDue due, boolean empty) {
                super.updateItem(due, empty);
                pseudoClassStateChanged(LATE, !empty && due != null && due.daysLate() > 0);
            }
        });
        dueSizing.install(dueTable);
    }

    /** "اليوم" on the day itself, otherwise how many days it has been waiting. */
    private String lateness(ExpenseRecurringDue due) {
        return due.daysLate() <= 0 ? text("expense.recurring.due.today")
                : LanguageManager.getInstance().getString("expense.recurring.late.days", due.daysLate());
    }

    private void buildTable() {
        table.setId("expense-recurring-table");
        table.setPlaceholder(new Label(text("expense.recurring.empty")));
        List<RowAction<ExpenseRecurring>> actions = RowAction.permitted(List.of(
                RowAction.of("update", AppIcon.EDIT, "app-primary-button",
                        AppPermissions.EXPENSES_RECURRING_MANAGE, this::edit),
                RowAction.of("expense.heading.toggle", AppIcon.SECURITY, "app-neutral-button",
                        AppPermissions.EXPENSES_RECURRING_MANAGE, this::toggleActive),
                RowAction.of("delete", AppIcon.DELETE, "app-neutral-button",
                        AppPermissions.EXPENSES_RECURRING_MANAGE, this::remove)));
        table.getColumns().setAll(List.of(
                RowActionsColumn.of("employee.column.actions", actions),
                Columns.number("code", ExpenseRecurring::id),
                Columns.text("expense.column.heading", ExpenseRecurring::headingPath),
                Columns.text("invoice.treasury", ExpenseRecurring::treasuryName),
                Columns.money("column.amount", ExpenseRecurring::amount),
                Columns.text("expense.recurring.frequency", template -> text(template.frequency().messageKey())),
                Columns.text("expense.recurring.day", template -> String.valueOf(template.dayOfMonth())),
                Columns.text("expense.recurring.start", template -> template.startDate().toString()),
                Columns.text("expense.recurring.end", template -> template.endDate() == null
                        ? text("expense.recurring.open.end") : template.endDate().toString()),
                Columns.text("expense.column.payee", ExpenseRecurring::payee),
                Columns.number("expense.recurring.recorded", template -> recorded.getOrDefault(template.id(), 0)),
                Columns.text("expense.heading.active", template -> text(template.active() ? "yes" : "no"))));
        table.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(ExpenseRecurring template, boolean empty) {
                super.updateItem(template, empty);
                pseudoClassStateChanged(STOPPED, !empty && template != null && !template.active());
            }
        });
        columnSizing.install(table);
    }

    @Override
    public void selectData() {
        try {
            comboTreasury.setItems(FXCollections.observableArrayList(expenseService.treasuries()));
            if (comboTreasury.getValue() == null) {
                comboTreasury.getSelectionModel().selectFirst();
            }
            // An employee is paid through the employee payment screen, so a template may not name one of
            // those headings - the draft refuses it, and offering it here would be a refusal in waiting.
            comboHeading.setItems(FXCollections.observableArrayList(headingService.forExpenses()));
            reload();
        } catch (Exception e) {
            report(e);
        }
    }

    private void reload() {
        try {
            List<ExpenseRecurring> templates = recurringService.all();
            recorded.clear();
            for (ExpenseRecurring template : templates) {
                recorded.put(template.id(), recurringService.recordedCount(template.id()));
            }
            table.setItems(FXCollections.observableArrayList(templates));
            columnSizing.layout(table);

            dueTable.setItems(FXCollections.observableArrayList(recurringService.due(LocalDate.now())));
            dueSizing.layout(dueTable);
        } catch (Exception e) {
            report(e);
        }
    }

    /** Must answer exactly 1, or the dialog treats a saved template as a failure and stays open. */
    @Override
    public int insertData() throws Exception {
        ExpenseHeading heading = comboHeading.getValue();
        TreasuryBalanceSummary treasury = comboTreasury.getValue();
        recurringService.save(new ExpenseRecurringDraft(editing, heading == null ? 0 : heading.id(),
                treasury == null ? 0 : treasury.id(), amount(txtAmount), txtPayee.getText(), txtNotes.getText(),
                comboFrequency.getValue(), day(), startDate.getValue(), endDate.getValue(), isActive.isSelected()));
        editing = 0;
        return 1;
    }

    /** An editable spinner keeps its last committed value when the text is typed and not tabbed out of. */
    private int day() {
        String typed = spinnerDay.getEditor().getText();
        try {
            return typed == null || typed.isBlank() ? spinnerDay.getValue() : Integer.parseInt(typed.strip());
        } catch (NumberFormatException notANumber) {
            return spinnerDay.getValue();
        }
    }

    @Override
    public void afterSaved() {
        reload();
        resetData();
    }

    private void edit(ExpenseRecurring template) {
        editing = template.id();
        comboHeading.getItems().stream().filter(heading -> heading.id() == template.headingId()).findFirst()
                .ifPresent(comboHeading.getSelectionModel()::select);
        comboTreasury.getItems().stream().filter(treasury -> treasury.id() == template.treasuryId()).findFirst()
                .ifPresent(comboTreasury.getSelectionModel()::select);
        txtAmount.setText(template.amount().toPlainString());
        comboFrequency.getSelectionModel().select(template.frequency());
        spinnerDay.getValueFactory().setValue(template.dayOfMonth());
        startDate.setValue(template.startDate());
        endDate.setValue(template.endDate());
        txtPayee.setText(template.payee());
        txtNotes.setText(template.notes());
        isActive.setSelected(template.active());
        editingLabel.setText(LanguageManager.getInstance()
                .getString("expense.recurring.editing", template.headingPath()));
        txtAmount.requestFocus();
    }

    private void toggleActive(ExpenseRecurring template) {
        try {
            recurringService.save(new ExpenseRecurringDraft(template.id(), template.headingId(),
                    template.treasuryId(), template.amount(), template.payee(), template.notes(),
                    template.frequency(), template.dayOfMonth(), template.startDate(), template.endDate(),
                    !template.active()));
            reload();
        } catch (Exception e) {
            report(e);
        }
    }

    /**
     * Deletes a template nothing was recorded from. One that has recorded something is refused by the
     * service, with the count in the message, and is stopped instead.
     */
    private void remove(ExpenseRecurring template) {
        try {
            if (!AllAlerts.confirmDelete()) {
                return;
            }
            recurringService.delete(template.id());
            reload();
        } catch (Exception e) {
            report(e);
        }
    }

    @Override
    public void resetData() {
        editing = 0;
        txtAmount.clear();
        txtPayee.clear();
        txtNotes.clear();
        endDate.setValue(null);
        isActive.setSelected(true);
        editingLabel.setText("");
    }

    @Override
    public @NotNull BooleanBinding checkDataToEnableButton() {
        return Bindings.createBooleanBinding(
                () -> comboHeading.getValue() == null
                        || comboTreasury.getValue() == null
                        || comboFrequency.getValue() == null
                        || startDate.getValue() == null
                        || amount(txtAmount).signum() <= 0,
                comboHeading.valueProperty(), comboTreasury.valueProperty(), comboFrequency.valueProperty(),
                startDate.valueProperty(), txtAmount.textProperty());
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

    /**
     * The entry screen, filled in from the template. The expense it saves carries the template's id, so
     * the period stops being due - that link is what "already recorded" means.
     * <p>
     * <b>The list is read again once that dialog closes</b>, and the dialog is modal, so this line runs
     * after the save. Without it the answered row stayed on screen exactly as it was - found by opening
     * the screen - and a person who records the rent and looks back at a reminder still saying it is due
     * has every reason to record it a second time.
     */
    private void recordNow(ExpenseRecurringDue due) {
        try {
            new com.hamza.account.openFxml.AddForAllApplication(0, ExpenseEntryController.from(due));
        } catch (Exception e) {
            report(e);
        }
        reload();
    }

    private static BigDecimal amount(TextField field) {
        String value = field.getText();
        return value == null || value.isBlank()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(com.hamza.controlsfx.others.DoubleSetting.parseDoubleOrDefault(value));
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

    private void report(Exception e) {
        AllAlerts.handleError(text("expense.error.operation"), e);
    }

    /**
     * A caption and the control it names, as one child of the {@code FlowPane}.
     * <p>
     * A {@code FlowPane} wraps between any two children, so loose captions get separated from their
     * controls: the budget screen opened with "الفترة" ending one line and its combo starting the next,
     * which reads as though it labelled the control before it. A pair cannot be split.
     */
    private static HBox field(String captionKey, javafx.scene.Node control) {
        HBox pair = new HBox(6, caption(captionKey), control);
        pair.setAlignment(Pos.CENTER_LEFT);
        return pair;
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
