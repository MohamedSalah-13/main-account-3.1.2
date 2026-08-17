package com.hamza.account.controller.reports;

import com.hamza.account.config.ThemeManager;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.returns.ReturnReason;
import com.hamza.account.features.returns.ReturnReasonReportService;
import com.hamza.account.features.returns.ReturnableRepository;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Groups a period's returns by {@link ReturnReason} - the report {@code return_reason}
 * exists to make possible, once {@code DialogReturnFromInvoice} started asking for one.
 * <p>
 * A plain {@link Stage}, not FXML - the same reasoning {@code DialogCashPaid} and
 * {@code DialogReturnFromInvoice} already follow for a one-off window: nothing here is
 * reused by another screen, so a separate view file would only be one more place to
 * keep in sync with the controller building it.
 */
public final class DialogReturnReasonsReport {

    private DialogReturnReasonsReport() {
    }

    public static void show(ReturnReasonReportService service) {
        var lang = LanguageManager.getInstance();
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(lang.getString("report.returns.reasons.title"));

        ToggleGroup sideGroup = new ToggleGroup();
        RadioButton salesToggle = new RadioButton(lang.getString("report.returns.reasons.sales"));
        salesToggle.setToggleGroup(sideGroup);
        salesToggle.setSelected(true);
        RadioButton purchaseToggle = new RadioButton(lang.getString("report.returns.reasons.purchases"));
        purchaseToggle.setToggleGroup(sideGroup);

        DatePicker fromPicker = new DatePicker(LocalDate.now().withDayOfMonth(1));
        DatePicker toPicker = new DatePicker(LocalDate.now());

        TableView<Row> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<Row, String> reasonColumn =
                new TableColumn<>(lang.getString("report.returns.reasons.column.reason"));
        reasonColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().label()));
        TableColumn<Row, String> countColumn =
                new TableColumn<>(lang.getString("report.returns.reasons.column.count"));
        countColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().count())));
        TableColumn<Row, String> totalColumn =
                new TableColumn<>(lang.getString("report.returns.reasons.column.total"));
        totalColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().total().toPlainString()));
        table.getColumns().setAll(List.of(reasonColumn, countColumn, totalColumn));

        Button refreshButton = new Button(lang.getString("report.returns.reasons.refresh"));
        refreshButton.setOnAction(event -> refresh(service, salesToggle.isSelected(),
                fromPicker.getValue(), toPicker.getValue(), table));

        HBox filters = new HBox(10,
                new Label(lang.getString("report.returns.reasons.from")), fromPicker,
                new Label(lang.getString("report.returns.reasons.to")), toPicker,
                salesToggle, purchaseToggle, refreshButton);
        filters.setPadding(new Insets(4, 0, 4, 0));

        Button closeButton = new Button(lang.getString("common.close"));
        closeButton.setOnAction(event -> stage.close());
        HBox actions = new HBox(closeButton);

        VBox root = new VBox(12, filters, table, actions);
        root.setPadding(new Insets(16));
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);

        Scene scene = new Scene(root, 640, 420);
        ChangeOrientation.sceneOrientation(scene);
        stage.setScene(scene);
        ThemeManager.apply(scene);

        refresh(service, true, fromPicker.getValue(), toPicker.getValue(), table);
        stage.show();
    }

    private static void refresh(ReturnReasonReportService service, boolean sales,
                                LocalDate from, LocalDate to, TableView<Row> table) {
        try {
            DocumentType type = sales ? DocumentType.SALES_RETURN : DocumentType.PURCHASE_RETURN;
            List<ReturnableRepository.ReasonCount> counts = service.summarize(type, from, to);
            table.setItems(FXCollections.observableArrayList(counts.stream()
                    .map(Row::of)
                    .toList()));
        } catch (Exception e) {
            AllAlerts.handleError(
                    LanguageManager.getInstance().getString("report.returns.reasons.title"), e);
        }
    }

    private record Row(String label, int count, BigDecimal total) {
        static Row of(ReturnableRepository.ReasonCount count) {
            String label = count.reason() == null
                    ? LanguageManager.getInstance().getString("report.returns.reasons.no.reason")
                    : count.reason().label();
            return new Row(label, count.count(), count.total());
        }
    }
}
