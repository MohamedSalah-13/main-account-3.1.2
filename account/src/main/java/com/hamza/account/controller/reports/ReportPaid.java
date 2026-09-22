package com.hamza.account.controller.reports;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.NamesTables;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.payment.PartyPaymentRow;
import com.hamza.account.features.party.payment.PartyPaymentsService;
import com.hamza.account.features.party.payment.PartyPaymentsSummary;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.ContentSizedColumns;
import com.hamza.account.table.TablePdfLayout;
import com.hamza.account.table.TablePdfReport;
import com.hamza.account.table.TableSetting;
import com.hamza.account.table.VisibleColumnsExcelWriter;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.Columns;
import com.hamza.controlsfx.util.ImageChoose;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The sidebar's "customer payments" and "supplier payments": every cash movement on the parties'
 * accounts over a period, with its count and total, printable and exportable.
 *
 * <p>It used to take its columns from {@code AccountData.updateTableView}, which inserts two of
 * them at fixed positions, and then keep four by index ({@code retainAll(get(0), get(1), get(4),
 * get(6))}) - so which figure a column showed depended on another class's insertion order. It listed
 * every debit and credit note as a payment of zero, wrote its total with {@code String.valueOf},
 * swallowed a failed search into the log, and its print button answered "not implemented". The rows
 * come from {@link PartyPaymentsService} now, the columns are built here by name, and printing and
 * exporting follow the columns on screen.</p>
 */
@FxmlPath(pathFile = "reports/report-paid.fxml")
public class ReportPaid {

    private static final String PAID_COLUMN = "partyPaymentPaid";

    private final PartyKind kind;
    private final String title;
    private final PartyPaymentsService service;
    private final ProgressIndicator progress = new ProgressIndicator();
    private final ContentSizedColumns<PartyPaymentRow> widths = new ContentSizedColumns<>();
    private List<PartyPaymentRow> shown = List.of();
    private int generation;

    @FXML
    private DatePicker dateFrom, dateTo;
    @FXML
    private Button btnSearch, btnPrint, btnExcel;
    @FXML
    private StackPane stackPane;
    @FXML
    private Text textCount, textTotal;
    @FXML
    private Label labelFrom, labelTo, labelCount, labelTotal, textTitle;
    @FXML
    private TableView<PartyPaymentRow> tableView;

    public ReportPaid(PartyKind kind, String title) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.title = title;
        this.service = new PartyPaymentsService();
    }

    @FXML
    public void initialize() {
        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);
        buildTable();
        otherSetting();
        progress.setMaxSize(64, 64);
        progress.setVisible(false);
        stackPane.getChildren().add(progress);
        showSummary(PartyPaymentsSummary.of(List.of()));
    }

    private void buildTable() {
        // An id of its own per side: an id-less table shares its saved widths with every other
        // id-less table in the package (TableSetting keys by the package's node).
        tableView.setId(kind == PartyKind.CUSTOMER ? "customerPaymentsTable" : "supplierPaymentsTable");
        tableView.getColumns().setAll(List.of(
                named("partyPaymentDate", Columns.date(NamesTables.DATE, PartyPaymentRow::date)),
                named("partyPaymentCode", Columns.number(NamesTables.CODE, PartyPaymentRow::partyId)),
                named("partyPaymentName", Columns.text(NamesTables.NAME, PartyPaymentRow::partyName)),
                named(PAID_COLUMN, Columns.money("paid", PartyPaymentRow::paid)),
                named("partyPaymentTreasury", Columns.text("invoice.treasury", PartyPaymentRow::treasuryName)),
                named("partyPaymentInvoice", Columns.text(NamesTables.CODE_INVOICE,
                        row -> row.invoiceNumber() > 0 ? String.valueOf(row.invoiceNumber()) : "")),
                named("partyPaymentNotes", Columns.text(NamesTables.NOTES, PartyPaymentRow::notes))));
        TableSetting.tableMenuSetting(getClass(), tableView);
        widths.install(tableView);
    }

    private static <S, V> TableColumn<S, V> named(String id, TableColumn<S, V> column) {
        column.setId(id);
        return column;
    }

    private void otherSetting() {
        textTitle.setText(title);
        var imageSetting = new Image_Setting();
        btnSearch.setGraphic(ImageChoose.createIcon(imageSetting.search));
        btnPrint.setGraphic(ImageChoose.createIcon(imageSetting.print));

        labelCount.setText(text("count"));
        labelTotal.setText(text("total"));
        labelFrom.setText(text("from"));
        labelTo.setText(text("to"));
        btnPrint.setText(text("print"));
        btnSearch.setText(text("search"));
        btnExcel.setText(text("report.export.excel"));

        btnSearch.setOnAction(event -> search());
        btnPrint.setOnAction(event -> print());
        btnExcel.setOnAction(event -> exportExcel());
    }

    /** Off the JavaFX thread; an answer to a search the user has since replaced is dropped. */
    private void search() {
        int mine = ++generation;
        var from = dateFrom.getValue();
        var to = dateTo.getValue();
        progress.setVisible(true);
        Task<List<PartyPaymentRow>> task = new Task<>() {
            @Override
            protected List<PartyPaymentRow> call() throws Exception {
                return service.payments(kind, from, to);
            }
        };
        task.setOnSucceeded(event -> {
            if (mine != generation) {
                return;
            }
            progress.setVisible(false);
            shown = task.getValue();
            tableView.getItems().setAll(shown);
            widths.layout(tableView);
            showSummary(PartyPaymentsSummary.of(shown));
        });
        task.setOnFailed(event -> {
            if (mine != generation) {
                return;
            }
            progress.setVisible(false);
            AllAlerts.handleError(text("report.error.search.statement.title"), task.getException());
        });
        TablePdfReport.start(task, "party-payments-report");
    }

    private void showSummary(PartyPaymentsSummary summary) {
        textCount.setText(String.valueOf(summary.movements()));
        textTotal.setText(Columns.money(summary.total()));
    }

    private void print() {
        if (shown.isEmpty()) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        File target = TablePdfReport.chooseTarget(tableView.getScene().getWindow(), title);
        if (target == null) {
            return;
        }
        TablePdfLayout layout = TablePdfLayout.from(tableView, shown, Set.of(), Set.of(PAID_COLUMN), text("total"));
        TablePdfReport.write(target, title, subtitle(), layout, () -> { });
    }

    private void exportExcel() {
        try {
            if (shown.isEmpty()) {
                throw new UserValidationException(text("party.error.no.data.export"));
            }
            int written = ExportData.exportDataToExcel(shown,
                    VisibleColumnsExcelWriter.of(title, tableView, Set.of(), shown));
            if (written >= 1) {
                AllAlerts.alertSaveWithMessage(text("party.export.excel.success"));
            }
        } catch (Exception e) {
            AllAlerts.handleError(text("report.export.excel"), e);
        }
    }

    private String subtitle() {
        return LanguageManager.getInstance().getString("report.period.from.to", dateFrom.getValue(), dateTo.getValue());
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
