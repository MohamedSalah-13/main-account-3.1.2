package com.hamza.account.controller.target;

import com.hamza.account.openFxml.FxmlPath;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.text.Text;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.function.ToDoubleFunction;

interface AddDataInterface<T> {
    ToDoubleFunction<T> getColumnValuePaid();

    ToDoubleFunction<T> getColumnValueAmount();

    default void printInvoice(TableView<T> tableView) {
    }
}

@RequiredArgsConstructor
@FxmlPath(pathFile = "target/target-table-view.fxml")
public class TargetTableController<T> {

    private final AddDataInterface<T> addDataInterface;
    @Getter
    @FXML
    private TableView<T> tableView;
    @FXML
    private Button btnPrint;
    @FXML
    private Text textSumPayment;
    @FXML
    private Text textSumAmount;
    @Setter
    private int columnIndex;
    @Setter
    private int columnIndex1;

    @FXML
    public void initialize() {
        tableView.itemsProperty().addListener((observable, oldValue, newValue) -> {
            var sum = tableView.getItems().stream().mapToDouble(addDataInterface.getColumnValuePaid()).sum();
            var sumAmount1 = tableView.getItems().stream().mapToDouble(addDataInterface.getColumnValueAmount()).sum();
            textSumPayment.setText(String.format("%.2f", sum));
            textSumAmount.setText(String.format("%.2f", sumAmount1));
        });

        btnPrint.setOnAction(event -> {
            addDataInterface.printInvoice(tableView);
            if (columnIndex != -1 && columnIndex1 != -1) {
                tableView.getColumns().get(columnIndex).setSortType(null);
                tableView.getColumns().get(columnIndex1).setSortType(null);
            }
            tableView.getSortOrder().clear();
            tableView.refresh();
            textSumPayment.setText("0");
            textSumAmount.setText("0");
        });

    }

}