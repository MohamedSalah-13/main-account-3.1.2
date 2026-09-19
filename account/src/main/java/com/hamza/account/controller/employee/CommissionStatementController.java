package com.hamza.account.controller.employee;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.delegate.CommissionStatementService;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.util.List;

/**
 * One delegate's commission, month by month: the approved figure, where it went, and what the
 * month comes to if it is computed again today.
 *
 * <p>Read-only, and the difference column is the point of it - see
 * {@link CommissionStatementService}. The sentence above the table says what a difference means,
 * because a column of non-zero numbers beside approved figures reads as an accusation otherwise.
 * The screen displays and does not decide: every figure is the service's.
 */
@Log4j2
public class CommissionStatementController implements AppSettingInterface {

    private final CommissionStatementService service = ServiceRegistry.get(CommissionStatementService.class);
    private final int employeeId;
    private final String employeeName;

    private final TableView<CommissionStatementService.Row> table = new TableView<>();
    private final ContentSizedColumns<CommissionStatementService.Row> columnSizing = new ContentSizedColumns<>();
    private final Label totalApproved = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();

    public CommissionStatementController(int employeeId, String employeeName) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
    }

    @Override
    public Pane pane() {
        buildTable();

        Label title = new Label(text("commission.statement.title") + " - " + employeeName);
        title.getStyleClass().add("party-screen-title");
        HBox header = new HBox(12, AppIcon.REPORT.graphic(24), title);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMaxWidth(Double.MAX_VALUE);
        header.getStyleClass().add("party-screen-header");

        Label hint = new Label(text("commission.statement.hint"));
        hint.getStyleClass().add("form-hint");
        hint.setWrapText(true);
        totalApproved.getStyleClass().add("form-label");
        VBox card = new VBox(6, hint, totalApproved);
        card.getStyleClass().addAll("app-card", "party-form-card");

        progress.setMaxSize(48, 48);
        StackPane content = new StackPane(table, progress);
        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("app-container");
        layout.setTop(new VBox(8, header, card));
        layout.setCenter(content);
        BorderPane.setMargin(content, new Insets(8, 0, 0, 0));

        StackPane screen = new StackPane(layout);
        screen.getStyleClass().addAll("app-root", "screen-employees");
        screen.getStylesheets().add(ThemeManager.getStylesheet());
        screen.setId("commission-statement");
        // Nine content-sized columns: at 760 the last one - how the month was posted - was cut.
        screen.setPrefSize(920, 560);

        Platform.runLater(this::load);
        return screen;
    }

    private void buildTable() {
        table.setId("commission-statement-table");
        table.setPlaceholder(new Label(text("commission.statement.empty")));
        table.getColumns().setAll(List.of(
                named("statement-month", Columns.text("delegate.performance.month", row -> row.period().toString())),
                named("statement-basis", Columns.text("commission.rule.basis",
                        row -> text(row.approved().basis().messageKey()))),
                named("statement-base", Columns.money("commission.run.column.base", row -> row.approved().baseAmount())),
                named("statement-tiers", Columns.text("commission.rule.tiers", row -> row.approved().tiersSnapshot())),
                named("statement-rate", Columns.money("delegate.performance.column.rate",
                        row -> row.approved().ratePercent())),
                named("statement-approved", Columns.money("commission.statement.column.approved",
                        row -> row.approved().amount())),
                named("statement-live", Columns.money("commission.statement.column.live",
                        CommissionStatementService.Row::live)),
                named("statement-difference", Columns.money("commission.statement.column.difference",
                        CommissionStatementService.Row::difference)),
                named("statement-posting", Columns.text("commission.run.column.posting",
                        row -> text(row.approved().posting().messageKey())))));
        columnSizing.install(table);
    }

    /** Off the JavaFX thread: the live figure is a query per month. */
    private void load() {
        Task<List<CommissionStatementService.Row>> task = new Task<>() {
            @Override
            protected List<CommissionStatementService.Row> call() throws Exception {
                return service.forDelegate(employeeId);
            }
        };
        task.setOnSucceeded(event -> {
            progress.setVisible(false);
            List<CommissionStatementService.Row> rows = task.getValue();
            table.setItems(FXCollections.observableArrayList(rows));
            columnSizing.layout(table);
            totalApproved.setText(text("commission.statement.total") + " " + Columns.money(rows.stream()
                    .map(row -> row.approved().amount()).reduce(BigDecimal.ZERO, BigDecimal::add)));
        });
        task.setOnFailed(event -> {
            progress.setVisible(false);
            Throwable error = task.getException();
            AllAlerts.handleError(text("commission.statement.title"),
                    error instanceof Exception ? (Exception) error : new Exception(error));
        });
        Thread worker = new Thread(task, "commission-statement");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public String title() {
        return text("commission.statement.title");
    }

    @Override
    public boolean resize() {
        return true;
    }

    @Override
    public String dialogStyleClass() {
        return "screen-employees";
    }

    private static <S, V> TableColumn<S, V> named(String id, TableColumn<S, V> column) {
        column.setId(id);
        return column;
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
