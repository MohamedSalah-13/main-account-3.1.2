package com.hamza.account.view.barcode;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.otherSetting.ButtonDeleteRow;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.button.button_column.ButtonColumn;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.Pane;
import javafx.util.converter.IntegerStringConverter;
import lombok.RequiredArgsConstructor;

import static com.hamza.controlsfx.util.ImageChoose.createIcon;

@RequiredArgsConstructor
public class PrintBarcode implements AppSettingInterface {

    private final ObservableList<PrintBarcodeModel> observableList;
    @FXML
    private Button btnPrint, btnClose;
    @FXML
    private TableView<PrintBarcodeModel> tableView;


    @FXML
    public void initialize() {
        otherSetting();
        edit();
        buttonGraphic();
    }

    private void otherSetting() {
        new TableColumnAnnotation().getTable(tableView, PrintBarcodeModel.class);
        tableView.setItems(observableList);
        tableView.setEditable(true);
        tableView.getSelectionModel().setCellSelectionEnabled(true);
        btnPrint.setText(Setting_Language.PRINT_BARCODE);
        btnClose.setText(Setting_Language.WORD_CLOSE);

        btnPrint.setOnAction(actionEvent -> print());
        btnClose.setOnAction(actionEvent -> btnClose.getScene().getWindow().hide());
        TableSetting.tableMenuSetting(getClass(), tableView);
        tableView.getColumns().add(new ButtonColumn<>(new ButtonDeleteRow() {
            @Override
            public void action(int i) {
                tableView.getItems().remove(i);
                tableView.refresh();
            }
        }));
    }

    private void buttonGraphic() {
        var images = new Image_Setting();
        btnPrint.setGraphic(createIcon(images.print));
        btnClose.setGraphic(createIcon(images.cancel));
    }

    private void print() {
        var selectedItems = tableView.getItems();
        for (PrintBarcodeModel printBarcodeModel : selectedItems) {
            new Print_Reports().printBarcode(printBarcodeModel.getBarcode(), printBarcodeModel.getName()
                    , String.valueOf(printBarcodeModel.getPrice()), printBarcodeModel.getQuantity());
        }
    }

    private void edit() {
        TableColumn<PrintBarcodeModel, Integer> column = (TableColumn<PrintBarcodeModel, Integer>) tableView.getColumns().get(3);
        column.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        column.setOnEditCommit(event -> {
            event.getRowValue().setQuantity(event.getNewValue());
        });
    }

    @Override
    public Pane pane() throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("print_barcode.fxml"));
        fxmlLoader.setController(this);
        return fxmlLoader.load();
    }

    @Override
    public String title() {
        return "طباعة ملصق الباركود";
    }

}
