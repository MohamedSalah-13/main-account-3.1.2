package com.hamza.controlsfx.controller;

import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.TableViewShowDataInt;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import lombok.extern.log4j.Log4j2;

import static com.hamza.controlsfx.table.TextSearch.searchTableFromExitedText;

@Log4j2
public class TableViewShowDataController<T> {

    private final TableViewShowDataInt<T> dataTable;
    private final ObservableList<T> dataList = FXCollections.observableArrayList();
    @FXML
    private Button btnClose;
    @FXML
    private Button btnPrint;
    @FXML
    private TableView<T> tableView;
    @FXML
    private Text textCount;
    @FXML
    private TextField textSearch;
    @FXML
    private Label labelSearch;
    @FXML
    private Text textTotalLabel;

    public TableViewShowDataController(TableViewShowDataInt<T> dataTable) throws DaoException {
        this.dataTable = dataTable;
        this.dataList.setAll(dataTable.dataList());
    }

    @FXML
    public void initialize() {
        otherSetting();
        getTableData();
        actionClose();
    }

    private void otherSetting() {
        LanguageManager languageManager = LanguageManager.getInstance();
        labelSearch.setText(languageManager.getString("search"));
        textSearch.setPromptText(languageManager.getString("search"));
        textTotalLabel.setText(languageManager.getString("total"));
        btnPrint.setText(languageManager.getString("print"));
        btnClose.setText(languageManager.getString("common.close"));
    }

    private void getTableData() {
        tableView.getColumns().addAll(dataTable.columns());
        tableView.setItems(dataList);
        textCount.setText(String.valueOf(dataList.size()));
    }

    private void actionClose() {
        btnClose.setOnAction(actionEvent -> btnClose.getScene().getWindow().hide());
        textSearch.setOnKeyReleased(event -> searchTableFromExitedText(tableView, textSearch.getText(), dataList));
        tableView.itemsProperty().addListener((observableValue, ts, t1) -> {
            textCount.setText(String.valueOf(tableView.getItems().size()));
        });

        btnPrint.setDisable(true);
    }
}
