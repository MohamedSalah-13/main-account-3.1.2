package com.hamza.account.controller.others;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Processes_Data;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.table.TableSetting;
import com.hamza.account.type.ProcessesDataType;
import com.hamza.account.type.TableType;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.filechooser.ImageChoose;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;
import lombok.extern.log4j.Log4j2;

import java.net.URL;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Predicate;

import static com.hamza.controlsfx.table.TextSearch.searchTableFromExitedText;

@Log4j2
@FxmlPath(pathFile = "process-view.fxml")
public class ProcessesController extends ServiceData implements Initializable {

    private final ObservableList<Processes_Data> observableListTable = FXCollections.observableArrayList();
    @FXML
    private TableView<Processes_Data> tableView;
    @FXML
    private TextField txtSearch;
    @FXML
    private ComboBox<String> boxAdmin, boxProcess, boxTable, comboSortedBy;
    @FXML
    private Label labelAdmin, labelProcess, labelSearch, labelTable, labelFrom, labelTo, labelSortBy;
    @FXML
    private Button btnSearch, btnDelete, btnClose;
    @FXML
    private StackPane stackPane;
    @FXML
    private RadioButton sortAscending, sortDescending;
    @FXML
    private DatePicker dateFrom, dateTo;
    private MaskerPaneSetting maskerPaneSetting;
    private FilteredList<Processes_Data> filteredTable;

    public ProcessesController(DaoFactory daoFactory) throws Exception {
        super(daoFactory);
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        otherSetting();
        loadData();
        getTable();
        addSorted();
        action();
        buttonGraphics();
    }

    private void otherSetting() {
        txtSearch.setPromptText(Setting_Language.WORD_SEARCH);
        labelAdmin.setText(Setting_Language.WORD_ADMIN);
        labelSearch.setText(Setting_Language.WORD_SEARCH);
        labelTable.setText(Setting_Language.WORD_FROM);
        labelFrom.setText(Setting_Language.WORD_FROM);
        labelTo.setText(Setting_Language.WORD_TO);
        labelSortBy.setText("فرز بواسطة");
        btnSearch.setText(Setting_Language.WORD_SEARCH);
        btnDelete.setText(Setting_Language.WORD_DELETE);
        btnClose.setText(Setting_Language.WORD_CLOSE);
        boxAdmin.setPromptText(Setting_Language.WORD_ADMIN);
        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);
    }

    private void addSorted() {
        List<String> stringList = List.of("الكود", "الاسم", "النوع", "العملية", "التاريخ");
        comboSortedBy.getItems().addAll(stringList);
        comboSortedBy.getSelectionModel().select(0);

        var usersNames = getUsersNames();
        boxAdmin.getItems().addAll(usersNames);

        boxProcess.setItems(FXCollections.observableArrayList(Arrays.stream(ProcessesDataType.values()).map(ProcessesDataType::getType).toList()));
        boxTable.setItems(FXCollections.observableArrayList(Arrays.stream(TableType.values()).map(TableType::getType).toList()));

        boxAdmin.getItems().addFirst(Setting_Language.WORD_ALL);
        boxProcess.getItems().addFirst(Setting_Language.WORD_ALL);
        boxTable.getItems().addFirst(Setting_Language.WORD_ALL);

        boxAdmin.getSelectionModel().selectFirst();
        boxProcess.getSelectionModel().selectFirst();
        boxTable.getSelectionModel().selectFirst();
    }

    private List<String> getUsersNames() {
        try {
            return usersService.getUsersNames();
        } catch (DaoException e) {
            log.error(e.getMessage());
            AllAlerts.alertError(e.getMessage());
            return List.of();
        }
    }

    private void getTable() {
        new TableColumnAnnotation().getTable(tableView, Processes_Data.class);
//        tableView.setItems(observableListTable);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        Callback<TableColumn.CellDataFeatures<Processes_Data, String>, ObservableValue<String>> columnDate = f -> new SimpleStringProperty(f.getValue().getCreated_at().toString());
        ColumnSetting.addColumn(tableView, Setting_Language.WORD_DATE, 1, columnDate);

        Callback<TableColumn.CellDataFeatures<Processes_Data, String>, ObservableValue<String>> column = f -> f.getValue().getUsersObject().usernameProperty();
        ColumnSetting.addColumn(tableView, Setting_Language.WORD_USERS, 2, column);

        Callback<TableColumn.CellDataFeatures<Processes_Data, String>, ObservableValue<String>> columnProcessesDataType = f -> f.getValue().getProcessesDataType().typeProperty();
        ColumnSetting.addColumn(tableView, Setting_Language.WORD_TYPE, 3, columnProcessesDataType);

        Callback<TableColumn.CellDataFeatures<Processes_Data, String>, ObservableValue<String>> columnTableType = f -> f.getValue().getTableType().typeProperty();
        ColumnSetting.addColumn(tableView, "من جدول", 4, columnTableType);

        filteredTable = new FilteredList<>(observableListTable);
        TableSetting.tableMenuSetting(getClass(), tableView);
    }

    private void buttonGraphics() {
        var image = new Image_Setting();
        btnClose.setGraphic(ImageChoose.createIcon(image.cancel));
        btnDelete.setGraphic(ImageChoose.createIcon(image.delete));
        btnSearch.setGraphic(ImageChoose.createIcon(image.search));
    }

    private void searchAction() {
        filteredTable.setPredicate(getAdminPredicate().and(getSelectedProcessPredicate()).and(getSelectedTablePredicate()).and(filterByDate()));
        SortedList<Processes_Data> sortedList = new SortedList<>(filteredTable);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);
    }

    private Predicate<Processes_Data> getAdminPredicate() {
        if (!boxAdmin.getSelectionModel().isEmpty()) {
            if (boxAdmin.getSelectionModel().getSelectedIndex() == 0) return t2 -> true;
            return processesData -> processesData.getUsersObject().getUsername().equals(boxAdmin.getSelectionModel().getSelectedItem());
        }
        return t2 -> false;
    }

    private Predicate<Processes_Data> getSelectedProcessPredicate() {
        if (!boxProcess.getSelectionModel().isEmpty()) {
            if (boxProcess.getSelectionModel().getSelectedIndex() == 0) return t2 -> true;
            return processesData -> processesData.getProcessesDataType().getType().equals(boxProcess.getSelectionModel().getSelectedItem());
        }
        return t2 -> false;
    }

    private Predicate<Processes_Data> getSelectedTablePredicate() {
        if (!boxTable.getSelectionModel().isEmpty()) {
            if (boxTable.getSelectionModel().getSelectedIndex() == 0) return t2 -> true;
            return processesData -> processesData.getTableType().getType().equals(boxTable.getSelectionModel().getSelectedItem());
        }
        return t2 -> false;
    }

    private Predicate<Processes_Data> filterByDate() {
        LocalDate dateFromValue = parseDate(dateFrom.getValue().toString());
        LocalDate dateToValue = parseDate(dateTo.getValue().toString());
        return t2 -> {
            LocalDate date = parseDate(t2.getCreated_at().toString().substring(0, 10));
            return (isDateInRange(date, dateFromValue, dateToValue));
        };
    }

    private boolean isDateInRange(LocalDate date, LocalDate dateFrom, LocalDate dateTo) {
        return (date.isEqual(dateFrom) || date.isAfter(dateFrom)) && (date.isEqual(dateTo) || date.isBefore(dateTo));
    }

    private LocalDate parseDate(String date) {
        return LocalDate.parse(date);
    }

    private void loadData() {
        maskerPaneSetting.showMaskerPane(() -> {
            var processesData = getProcessesData();
            observableListTable.setAll(processesData);
        });
    }

    private List<Processes_Data> getProcessesData() {
        try {
            return processService.getProcessesData();
        } catch (DaoException e) {
            log.error(e.getMessage());
            AllAlerts.alertError(e.getMessage());
            return List.of();
        }
    }

    private void action() {
        txtSearch.setOnKeyReleased(event -> searchTableFromExitedText(tableView, txtSearch.getText(), observableListTable));
        btnDelete.setOnAction(actionEvent -> deleteProcessesData());
        btnClose.setOnAction(actionEvent -> btnClose.getScene().getWindow().hide());
        btnDelete.disableProperty().bind(tableView.getSelectionModel().selectedItemProperty().isNull());
        btnSearch.setOnAction(actionEvent -> searchAction());
    }

    private void deleteProcessesData() {
        try {
            List<Processes_Data> processesDataList = tableView.getSelectionModel().getSelectedItems();
            if (!processesDataList.isEmpty()) {
                var list = processesDataList.stream().map(Processes_Data::getId).toList();
                Integer[] ids = list.toArray(new Integer[0]);
//                System.out.println(ids);
                var i = processService.deleteInRangeId(ids);
                if (i > 0) {
                    AllAlerts.alertSave();
                    maskerPaneSetting.showMaskerPane(() -> {
                        var processesData = getProcessesData();
                        observableListTable.setAll(processesData);
                    });
                    log.info("processes deleted");
                }
            }
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
        }
    }
}
