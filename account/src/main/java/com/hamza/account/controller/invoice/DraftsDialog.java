package com.hamza.account.controller.invoice;

import com.hamza.account.controller.invoice.InvoiceDraftService.DraftSummary;
import com.hamza.account.controller.invoice.InvoiceDraftService.Type;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * حوار بسيط لاستعراض مسودات الفواتير المفتوحة حسب النوع (مشتريات/مبيعات)
 * مع إمكانية استرجاع مسودة محددة أو حذفها.
 */
public class DraftsDialog {

    public static Optional<DraftSummary> show(Type initialType) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("المسودات المحفوظة");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Top: type selector
        HBox top = new HBox(8);
        ComboBox<Type> typeCombo = new ComboBox<>(FXCollections.observableArrayList(Type.values()));
        typeCombo.getSelectionModel().select(initialType);
        top.getChildren().addAll(new Label("النوع:"), typeCombo);
        root.setTop(top);

        // Center: table
        TableView<DraftSummary> table = new TableView<>();
        TableColumn<DraftSummary, String> colCode = new TableColumn<>("الكود");
        colCode.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().code));

        TableColumn<DraftSummary, String> colName = new TableColumn<>("الاسم");
        colName.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().name));

        TableColumn<DraftSummary, String> colDate = new TableColumn<>("التاريخ");
        colDate.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().invoiceDate));

        TableColumn<DraftSummary, String> colTotal = new TableColumn<>("الإجمالي");
        colTotal.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.valueOf(c.getValue().total)));

        TableColumn<DraftSummary, String> colItems = new TableColumn<>("عدد الأصناف");
        colItems.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.valueOf(c.getValue().itemsCount)));

        TableColumn<DraftSummary, String> colUpdated = new TableColumn<>("آخر تحديث");
        colUpdated.setCellValueFactory(c -> {
            String s = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(c.getValue().lastModifiedMillis));
            return new javafx.beans.property.SimpleStringProperty(s);
        });

        table.getColumns().addAll(colCode, colName, colDate, colTotal, colItems, colUpdated);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        root.setCenter(table);

        // Bottom: buttons
        Button btnRestore = new Button("استرجاع");
        Button btnDelete = new Button("حذف");
        Button btnClose = new Button("إغلاق");
        btnRestore.setDisable(true);
        btnDelete.setDisable(true);
        HBox bottom = new HBox(10, btnRestore, btnDelete, new HBox(), btnClose);
        bottom.setPadding(new Insets(10, 0, 0, 0));
        root.setBottom(bottom);
        HBox.setHgrow(bottom.getChildren().get(2), javafx.scene.layout.Priority.ALWAYS);

        // Data loading
        Runnable reload = () -> {
            List<DraftSummary> summaries = InvoiceDraftService.listSummaries(typeCombo.getValue());
            table.setItems(FXCollections.observableArrayList(summaries));
        };
        typeCombo.valueProperty().addListener((obs, o, n) -> reload.run());
        reload.run();

        // Selection
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            boolean has = n != null;
            btnRestore.setDisable(!has);
            btnDelete.setDisable(!has);
        });

        final DraftSummary[] result = new DraftSummary[1];

        btnRestore.setOnAction(e -> {
            result[0] = table.getSelectionModel().getSelectedItem();
            stage.close();
        });

        btnDelete.setOnAction(e -> {
            DraftSummary sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) {
                InvoiceDraftService.clear(sel.type, sel.code);
                reload.run();
            }
        });

        btnClose.setOnAction(e -> stage.close());

        Scene scene = new Scene(root, 820, 420);
        stage.setScene(scene);
        stage.showAndWait();

        return Optional.ofNullable(result[0]);
    }
}
