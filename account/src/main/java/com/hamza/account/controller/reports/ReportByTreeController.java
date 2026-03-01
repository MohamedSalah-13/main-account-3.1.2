package com.hamza.account.controller.reports;

import com.hamza.account.config.FxmlConstants;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.interfaces.ReportTreeInterface;
import com.hamza.account.interfaces.api.NameData;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.service.NameService;
import com.hamza.account.table.TableSetting;
import com.hamza.account.type.TableName;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.TreeTable;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.log4j.Log4j2;
import org.controlsfx.control.SearchableComboBox;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Predicate;

import static com.hamza.account.config.PropertiesName.getSettingExpanded;
import static com.hamza.account.config.PropertiesName.setSettingExpanded;

/**
 * @param <T1> for purchase or account
 * @param <T3> for Names (Customers or Suppliers)
 */
@Log4j2
@FxmlPath(pathFile = "reports/report-print.fxml")
public class ReportByTreeController<T1, T3> implements Initializable {

    private final ReportTreeInterface<T1, T3> reportTreeInterface;
    private final NameService nameService;
    private final ObservableList<T1> observableList = FXCollections.observableArrayList();
    private TreeItem<T1> treeItem;
    @FXML
    private TreeTableView<T1> treeView;
    @FXML
    private Button btnSearchName;
    @FXML
    private DatePicker dateFrom, dateTo;
    @FXML
    private CheckBox checkExpanded;
    @FXML
    private VBox root;
    @FXML
    private Label labelFrom, labelTo, labelName;
    @FXML
    private SearchableComboBox<String> comboName, comboDetails;
    @FXML
    private StackPane stackPane;

    private ToolbarReportsNameController reportsToolbar;
    private MaskerPaneSetting maskerPaneSetting;

    public ReportByTreeController(DataPublisher dataPublisher, ReportTreeInterface<T1, T3> reportTreeInterface, NameData nameData) {
        this.reportTreeInterface = reportTreeInterface;
        this.nameService = new NameService<>(nameData);
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        loadToolbarName();
        createTree();
        otherSetting();
        nameSetting();
        comboDetailsAction();
        TableSetting.tableMenuSetting(getClass(), treeView);
    }

    private void loadToolbarName() {
        try {
            FXMLLoader loader = new FxmlConstants().toolbarReports;
            Node content = loader.load();
            reportsToolbar = loader.getController();
            root.getChildren().addFirst(content);
        } catch (IOException e) {
            log.error("Failed to load toolbar-reports.fxml", e);
            AllAlerts.alertError("Failed to load toolbar-reports.fxml");
        }
    }

    private void createTree() {
        treeView.getColumns().clear();
        TreeTable.createTable(treeView, reportTreeInterface.getColumnDefinitions());
        reportTreeInterface.addColumns(treeView);

        treeItem = new TreeItem<>(reportTreeInterface.loadTreeRoot());
        treeView.setRoot(treeItem);

//        initializeColumnCellFactoryInteger(0, treeView);
//        initializeColumnCellFactory(4, treeView);
//        initializeColumnCellFactory(5, treeView);
//        initializeColumnCellFactory(6, treeView);
//        initializeColumnCellFactory(7, treeView);
//        initializeColumnCellFactory(8, treeView);
    }

    private void otherSetting() {
        Button btnPrintTotals = new Button(Setting_Language.PRINT_TOTALS);
        btnPrintTotals.setVisible(reportTreeInterface.showData());
        root.getStylesheets().add(reportTreeInterface.styleSheet());

        List<T3> nameAll = new java.util.ArrayList<>();
        try {
            nameAll = reportTreeInterface.listNames();
        } catch (Exception e) {
            log.error(e.getMessage());
            AllAlerts.alertError(e.getMessage());
        }
        comboName.getItems().add(Setting_Language.WORD_ALL);
        comboName.getItems().addAll(nameService.getNames(nameAll));
        comboName.getSelectionModel().selectFirst();

        btnSearchName.setOnAction(event -> searchInDateShowData());

        checkExpanded.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
            setSettingExpanded(t1);
            for (int i = 0; i < treeView.getRoot().getChildren().size(); i++) {
                TreeItem<?> item = treeView.getRoot().getChildren().get(i);
                item.setExpanded(!t1);
            }
            treeView.getRoot().setExpanded(t1);
        });

        btnPrintTotals.setOnAction(actionEvent -> {
            try {
                reportTreeInterface.print_totals();
            } catch (Exception e) {
                log.error(e.getMessage());
                AllAlerts.alertError(e.getMessage());
            }
        });

        reportsToolbar.setReportToolbar(new ToolbarReportsNameInterface() {
            @Override
            public String setTitle() {
                return reportTreeInterface.nameTitle();
            }

            @Override
            public void print() throws Exception {
                reportTreeInterface.print();
            }

            @Override
            public void refresh() {
                searchInDateShowData();
            }
        }, btnPrintTotals);
    }

    private void nameSetting() {
        checkExpanded.setSelected(getSettingExpanded());
        checkExpanded.setText(Setting_Language.WORD_EXPAND);
        labelFrom.setText(Setting_Language.WORD_FROM);
        labelTo.setText(Setting_Language.WORD_TO);
        labelName.setText(Setting_Language.WORD_NAME);
        btnSearchName.setText(Setting_Language.WORD_SEARCH);
        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);
        dateFrom.setValue(DateSetting.firstDateInMonth);
    }

    private void comboDetailsAction() {
        BooleanBinding binding = new BooleanBinding() {
            @Override
            protected boolean computeValue() {
                return reportTreeInterface.showData();
            }
        };

        comboDetails.disableProperty().bind(binding.not());
        comboDetails.getItems().add(Setting_Language.WORD_ALL);
        var values = TableName.values();
        for (TableName tableName : values) {
            comboDetails.getItems().add(tableName.getType());
        }
    }

    private void searchInDateShowData() {
        if (dateFrom.getValue().isAfter(dateTo.getValue())) {
            AllAlerts.alertError("خطاء فى التاريخ");
            return;
        }

        maskerPaneSetting.showMaskerPane(() -> {
            observableList.clear();
            var reportTreeList = getReportTreeList()
                    .stream()
                    .filter(filterByName())
                    .filter(filterByDetails())
                    .toList();
            observableList.addAll(reportTreeList);
        });
        afterLoadData();
    }

    private Predicate<T1> filterByName() {
        return t1 -> {
            if (comboName.getSelectionModel().isEmpty()) {
                return true;
            }
            if (comboName.getSelectionModel().getSelectedIndex() == 0) {
                return true;
            }
            return reportTreeInterface.filterListByName(t1, comboName.getSelectionModel().getSelectedItem());
        };
    }

    private Predicate<T1> filterByDetails() {
        return t1 -> {
            if (comboDetails.getSelectionModel().isEmpty()) {
                return true;
            }
            if (comboDetails.getSelectionModel().getSelectedIndex() == 0) {
                return true;
            }
            return reportTreeInterface.filterListByTableName(t1, comboDetails.getSelectionModel().getSelectedItem());
        };
    }

    private List<T1> getReportTreeList() {
        try {
            return reportTreeInterface.listTree(dateFrom.getValue().toString(), dateTo.getValue().toString());
        } catch (Exception e) {
            log.error(e.getMessage(), e.getCause());
        }
        return List.of();
    }

    private void afterLoadData() {
        maskerPaneSetting.getVoidTask().setOnSucceeded(workerStateEvent -> {
//            SortedList<T1> sortedList = new SortedList<>(filteredTable);
            treeItem = reportTreeInterface.treeItemMain(observableList);
            treeView.setRoot(treeItem);
            treeItem.setExpanded(checkExpanded.isSelected());
            reportTreeInterface.addItemInTree(treeItem, observableList);

            if (treeView.getRoot() != null) {
                treeView.getRoot().setExpanded(checkExpanded.isSelected());
                treeView.getRoot().getChildren().forEach(t1TreeItem -> t1TreeItem.setExpanded(checkExpanded.isSelected()));
            }
        });

        maskerPaneSetting.getVoidTask().setOnFailed(workerStateEvent -> {
            if (treeView.getRoot() != null) {
                treeView.getRoot().setExpanded(checkExpanded.isSelected());
                treeView.getRoot().getChildren().forEach(t1TreeItem -> t1TreeItem.setExpanded(checkExpanded.isSelected()));
            }
        });
    }

}
