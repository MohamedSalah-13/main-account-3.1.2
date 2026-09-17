package com.hamza.account.controller.expense;

import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.ExpensesChanged;
import com.hamza.account.features.events.TreasuriesChanged;
import com.hamza.account.features.expense.ExpenseBatchDraft;
import com.hamza.account.features.expense.ExpenseBatchLineRefused;
import com.hamza.account.features.expense.ExpenseEntry;
import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.account.features.expense.ExpenseHeadingService;
import com.hamza.account.features.expense.ExpenseService;
import com.hamza.account.openFxml.AddInterface;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.RowAction;
import com.hamza.account.table.RowActionsColumn;
import com.hamza.account.treasury.DefaultTreasury;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.table.Columns;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import static com.hamza.controlsfx.others.DateSetting.dateAction;
import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

/**
 * Several expenses entered together and saved as one: the month's bills, a day's petty cash.
 * <p>
 * <b>A batch is how the screen is filled, not how the data is shaped</b> - decision ق-٦. Each line is
 * saved as the ordinary expense it is, all of them in one transaction, and a refused line refuses the
 * batch with its number. A voucher with lines would have been a new header table, and the shift journal,
 * the period lock and the delete registry all work on "one row, one movement of cash".
 * <p>
 * Lines are added from a form rather than typed into cells: a line goes through the same
 * {@link ExpenseEntry#parse} the single entry does, and Enter on the last field adds it and returns to
 * the heading, so the keyboard never leaves the form. What a batch is - its total, what it asks of each
 * till, its ceiling - is {@link ExpenseBatchDraft}'s, which has no JavaFX and a test.
 */
@Log4j2
@FxmlPath(pathFile = "expense-batch.fxml")
public class ExpenseBatchController implements AddInterface {

    /** A dotted lower-case identifier: what a refusal carrying a message key looks like. */
    private static final Pattern KEY_SHAPE = Pattern.compile("[a-z0-9_]+(\\.[a-z0-9_]+)+");

    private final ExpenseService expenseService = ServiceRegistry.get(ExpenseService.class);
    private final ExpenseHeadingService headingService = ServiceRegistry.get(ExpenseHeadingService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);

    private final ExpenseBatchDraft draft = new ExpenseBatchDraft();
    private final ObservableList<ExpenseBatchDraft.Line> lines = FXCollections.observableArrayList();

    private final DatePicker date = new DatePicker(LocalDate.now());
    private final ComboBox<TreasuryBalanceSummary> comboTreasury = new ComboBox<>();
    private final Label balance = new Label();
    private final ComboBox<ExpenseHeading> comboHeading = new ComboBox<>();
    private final TextField txtAmount = new TextField();
    private final TextField txtPayee = new TextField();
    private final TextField txtReference = new TextField();
    private final TextField txtNotes = new TextField();
    private final Button add = new Button();
    private final TableView<ExpenseBatchDraft.Line> table = new TableView<>(lines);
    private final ContentSizedColumns<ExpenseBatchDraft.Line> columnSizing = new ContentSizedColumns<>();
    private final Label totals = new Label();

    @FXML
    private VBox box;
    @FXML
    private StackPane stackPane;

    @FXML
    public void initialize() {
        otherSetting();
        selectData();
    }

    @Override
    public void otherSetting() {
        stackPane.getStyleClass().add("screen-expenses");

        dateAction(date);
        setTextFormatter(txtAmount);
        balance.getStyleClass().add("app-readonly-amount");
        txtAmount.setPromptText(text("column.amount"));
        txtPayee.setPromptText(text("expense.column.payee"));
        txtReference.setPromptText(text("expense.column.reference"));
        txtNotes.setPromptText(text("column.notes"));
        comboHeading.setPromptText(text("expense.column.heading"));
        comboHeading.setConverter(converter(heading -> heading == null ? "" : heading.path()));
        comboTreasury.setConverter(converter(treasury -> treasury == null ? "" : treasury.name()));
        comboTreasury.valueProperty().addListener((observable, old, treasury) -> balance.setText(
                treasury == null || treasury.balance() == null ? "—" : Columns.money(treasury.balance())));
        comboHeading.setPrefWidth(220);
        HBox.setHgrow(txtNotes, Priority.ALWAYS);

        add.setText(text("expense.batch.add"));
        add.setGraphic(AppIcon.ADD.graphic());
        add.getStyleClass().addAll("app-primary-button", "party-primary-button");
        add.setMinWidth(Region.USE_PREF_SIZE);
        add.setOnAction(event -> addLine());

        buildTable();
        box.getChildren().setAll(header(), sharedBar(), lineBar(), table, footer());
        VBox.setVgrow(table, Priority.ALWAYS);

        // One receipt after another: the heading, the amount, to whom, its number, a note - and Enter on the
        // add button adds the line and returns to the heading, below in addLine().
        whenEnterPressed(comboHeading, txtAmount, txtPayee, txtReference, txtNotes, add);
        loadPickers();
        showTotals();
        Platform.runLater(comboHeading::requestFocus);
    }

    private HBox header() {
        Label title = new Label(text("expense.batch.title"));
        title.getStyleClass().add("party-screen-title");
        HBox bar = new HBox(12, AppIcon.SELECT_ALL.graphic(24), title);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("party-screen-header");
        return bar;
    }

    /** What the lines share unless a line says otherwise: the day and the till. */
    private VBox sharedBar() {
        HBox row = new HBox(8, caption("date"), date, caption("invoice.treasury"), comboTreasury,
                caption("expense.balance"), balance);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(row);
        card.getStyleClass().addAll("app-card", "party-form-card");
        return card;
    }

    private VBox lineBar() {
        HBox row = new HBox(8, comboHeading, txtAmount, txtPayee, txtReference, txtNotes, add);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(row);
        card.getStyleClass().addAll("app-card", "party-form-card");
        return card;
    }

    private HBox footer() {
        HBox bar = new HBox(12, totals);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().addAll("summary-card", "party-summary-bar");
        return bar;
    }

    private void buildTable() {
        table.setId("expense-batch-table");
        table.setPlaceholder(new Label(text("expense.batch.empty")));
        table.getColumns().setAll(List.of(
                RowActionsColumn.of("employee.column.actions", List.of(
                        RowAction.of("expense.batch.remove", AppIcon.DELETE, "app-neutral-button", null,
                                this::removeLine))),
                Columns.text("date", line -> line.entry().date().toString()),
                Columns.text("expense.column.heading", ExpenseBatchDraft.Line::headingPath),
                Columns.money("column.amount", line -> line.entry().amount()),
                Columns.text("invoice.treasury", ExpenseBatchDraft.Line::treasuryName),
                Columns.text("expense.column.payee", line -> line.entry().payee()),
                Columns.text("expense.column.reference", line -> line.entry().referenceNo()),
                Columns.text("column.notes", line -> line.entry().notes())));
        columnSizing.install(table);
    }

    private void loadPickers() {
        try {
            comboTreasury.setItems(FXCollections.observableArrayList(expenseService.treasuries()));
            comboTreasury.getItems().stream().filter(treasury -> treasury.id() == DefaultTreasury.ID)
                    .findFirst().ifPresentOrElse(comboTreasury.getSelectionModel()::select,
                            () -> comboTreasury.getSelectionModel().selectFirst());
            comboHeading.setItems(FXCollections.observableArrayList(headingService.forExpenses()));
        } catch (Exception e) {
            report(e);
        }
    }

    /** Parses the form into a line, as the single entry would, and clears what identifies one receipt. */
    private void addLine() {
        try {
            ExpenseHeading heading = comboHeading.getValue();
            TreasuryBalanceSummary treasury = comboTreasury.getValue();
            ExpenseEntry entry = ExpenseEntry.parse(0, date.getValue(), heading == null ? 0 : heading.id(),
                    treasury == null ? 0 : treasury.id(), amount(txtAmount), txtPayee.getText(),
                    txtReference.getText(), txtNotes.getText());
            draft.add(entry, heading == null ? "" : heading.path(), treasury == null ? "" : treasury.name());
            lines.setAll(draft.lines());
            columnSizing.layout(table);
            showTotals();
            txtAmount.clear();
            txtPayee.clear();
            txtReference.clear();
            txtNotes.clear();
            Platform.runLater(comboHeading::requestFocus);
        } catch (Exception e) {
            report(e);
        }
    }

    private void removeLine(ExpenseBatchDraft.Line line) {
        draft.remove(lines.indexOf(line));
        lines.setAll(draft.lines());
        showTotals();
    }

    private void showTotals() {
        totals.setText(LanguageManager.getInstance()
                .getString("expense.batch.totals", draft.size(), Columns.money(draft.total())));
    }

    @Override
    public void selectData() {
        // Nothing to open: a batch is always new. A saved expense is corrected one at a time.
    }

    /** Must answer exactly 1, or the dialog treats a saved batch as a failure and stays open. */
    @Override
    public int insertData() throws Exception {
        if (draft.isEmpty()) {
            throw new UserValidationException(text("expense.batch.error.empty"));
        }
        if (!confirmTillsCanCover()) {
            return 0;
        }
        try {
            expenseService.createBatch(draft.entries());
        } catch (ExpenseBatchLineRefused refused) {
            // Formatted here, at the edge: the service hands over a line number and the refusal as thrown.
            throw new UserValidationException(LanguageManager.getInstance()
                    .getString("expense.batch.error.line", refused.line(), readable(refused.userMessage())),
                    refused);
        }
        return 1;
    }

    /**
     * Decision م-١, asked once per till over what the batch takes from it together - three bills of 400
     * each fit a till holding 1,000 one at a time, and do not fit together.
     */
    private boolean confirmTillsCanCover() throws Exception {
        StringBuilder shortTills = new StringBuilder();
        for (Map.Entry<Integer, BigDecimal> asked : draft.totalsByTreasury().entrySet()) {
            BigDecimal shortfall = expenseService.shortfall(asked.getKey(), asked.getValue(), 0);
            if (shortfall.signum() > 0) {
                String name = draft.lines().stream()
                        .filter(line -> line.entry().treasuryId() == asked.getKey())
                        .map(ExpenseBatchDraft.Line::treasuryName).findFirst().orElse("");
                shortTills.append('\n').append(name).append(": ").append(Columns.money(shortfall));
            }
        }
        return shortTills.isEmpty() || AllAlerts.confirm_all(text("expenses"),
                text("expense.batch.confirm.short") + shortTills);
    }

    @Override
    public void afterSaved() {
        if (eventBus != null) {
            eventBus.publish(new ExpensesChanged());
            eventBus.publish(new TreasuriesChanged());
        }
        resetData();
    }

    @Override
    public void resetData() {
        draft.clear();
        lines.clear();
        showTotals();
    }

    @Override
    public @NotNull BooleanBinding checkDataToEnableButton() {
        return Bindings.isEmpty(lines);
    }

    @Override
    public boolean resize() {
        return true;
    }

    @Override
    public String dialogStyleClass() {
        return "screen-expenses";
    }

    /** A message key becomes its sentence; a sentence stays as it is. */
    private static String readable(String message) {
        if (message == null) {
            return "";
        }
        var bundle = LanguageManager.getInstance().getResourceBundle();
        return KEY_SHAPE.matcher(message).matches() && bundle.containsKey(message)
                ? bundle.getString(message) : message;
    }

    private static Label caption(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        return label;
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

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
