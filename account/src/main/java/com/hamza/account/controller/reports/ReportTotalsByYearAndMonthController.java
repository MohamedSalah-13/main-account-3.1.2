package com.hamza.account.controller.reports;

import com.hamza.account.controller.reports.model.TableTotals;
import com.hamza.account.features.export.ReportExportService;
import com.hamza.account.interfaces.ReportTotalsInterface;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.TotalDesignInterface;
import com.hamza.account.interfaces.implReportTotals.SearchByDay;
import com.hamza.account.interfaces.implReportTotals.SearchByYear;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.dateTime.DateUtils;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.net.URL;
import java.util.*;

@Log4j2
@FxmlPath(pathFile = "reports/reportTotalsByYear.fxml")
public class ReportTotalsByYearAndMonthController<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount> implements Initializable {

    private final Print_Reports ptPrintReports;
    private final TotalDesignInterface<T2> anInterface;
    private final List<T2> listTotals;
    private final FilteredList<TableTotals> filteredList;
    private final ObservableList<TableTotals> tableTotals = FXCollections.observableArrayList();
    private final DataInterface<T1, T2, T3, T4> dataInterface;
    private ReportTotalsInterface<T2> reportTotalsInterface;
    @FXML
    private TableView<TableTotals> tableView;
    @FXML
    private ComboBox<Integer> comboBox;
    @FXML
    private Label labelYear;
    @FXML
    private Button btnPrint, btnSearch;
    @FXML
    private Label textStart;
    @FXML
    private CheckBox checkZeroData;
    @FXML
    private StackPane stackPane;
    @FXML
    private RadioButton radioMonth, radioYear;
    private MaskerPaneSetting maskerPaneSetting;
    private int year;
    private ReportExportService reportExportService;

    public ReportTotalsByYearAndMonthController(DataInterface<T1, T2, T3, T4> dataInterface) throws Exception {
        this.dataInterface = dataInterface;
        this.anInterface = dataInterface.totalDesignInterface();
        this.listTotals = anInterface.dataList();
        this.ptPrintReports = new Print_Reports();
        this.filteredList = new FilteredList<>(tableTotals);
        this.reportTotalsInterface = new SearchByYear<>(dataInterface, dataInterface.filterDateInterface());
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        reportExportService = new ReportExportService();
        getTable();
        other_setting();
        TableSetting.tableMenuSetting(getClass(), tableView);
    }

    private void getTable() {
        tableView.getColumns().clear();
        new TableColumnAnnotation().getTable(tableView, TableTotals.class);

//        new RowColor().customiseRowByRow(tableView, new RowColorInterface<>() {
//            @Override
//            public boolean checkRow(TableTotals tableTotals) {
//                if (tableTotals != null)
//                    return tableTotals.getName().equals("الاجمالى");
//                return false;
//            }
//        });

//        tableView.getStylesheets().add(reportTotalsInterface.style_sheet());

        for (int i = 0; i < tableView.getColumns().size(); i++) {
            tableView.getColumns().get(i).getStyleClass().add("name-column-center");
            tableView.getColumns().get(i).setMinWidth(100);
        }
        tableView.getColumns().getFirst().getStyleClass().add("name-column");
        tableView.getColumns().getLast().getStyleClass().add("name-column");

        SortedList<TableTotals> sortedList = new SortedList<>(tableTotals, Comparator.comparing(TableTotals::getName));
        filteredList.setPredicate(tableTotals1 -> true);
        sortedList.setComparator(Comparator.comparing(TableTotals::getName));
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);
//        tableView.setItems(tableTotals);
        tableView.refresh();
    }

    private void other_setting() {
        String name = reportTotalsInterface.title();
        btnPrint.setText(Setting_Language.WORD_PRINT);
        btnSearch.setText(Setting_Language.WORD_SEARCH);
        labelYear.setText(Setting_Language.WORD_SEARCH_DATE);
        textStart.setText(name);
        checkZeroData.setText("بيانات صفر");


        // combo setting
        List<String> totalBuyList = listTotals.stream().map(anInterface.totalsDataInterface().getDateFunction()).toList();
        ObservableList<Integer> years = DateUtils.getDistinctYears(totalBuyList).sorted();
        comboBox.setItems(years);

        Optional<Integer> max = comboBox.getItems().stream().max(Integer::compareTo);
        max.ifPresent(integer -> {
            year = integer;
            comboBox.getSelectionModel().select(integer);
            reportTotalsInterface.getYear(integer);
        });


        comboBox.valueProperty().addListener((observableValue, integer, t1) -> {
            year = t1;
            reportTotalsInterface.getYear(year);
        });
//        btnPrint.setOnAction(actionEvent -> exportToPdf());
        btnPrint.setOnAction(actionEvent -> ptPrintReports.printReportByMonth(tableView.getItems(), textStart.getText()));

        btnSearch.setOnAction(actionEvent -> {
            if (comboBox.getSelectionModel().isEmpty()) {
                AllAlerts.alertError(Setting_Language.PLEASE_INSERT_ALL_DATA);
                comboBox.requestFocus();
            } else {
                getData();
//                textStart.setText(name + year);
            }
        });


        checkZeroData.setSelected(true);
        checkZeroData.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                filteredList.setPredicate(tableTotals -> true);
            } else {
                filteredList.setPredicate(tableTotals -> tableTotals.getTotals() > 0);
            }
            tableView.setItems(filteredList);
            tableView.refresh();
        });

        radioYear.selectedProperty().addListener((observable, oldValue, newValue) -> {
            reportTotalsInterface = new SearchByYear<>(dataInterface, dataInterface.filterDateInterface());
        });

        radioMonth.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
            reportTotalsInterface = new SearchByDay<>(dataInterface, dataInterface.filterDateInterface());
        });
    }

    /**
     * تصدير التقرير إلى PDF
     */
    private void exportToPdf() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("حفظ التقرير");
        fileChooser.setInitialFileName(textStart.getText() + "_" + year + ".pdf");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );

        File file = fileChooser.showSaveDialog(tableView.getScene().getWindow());

        if (file != null) {
//            maskerPaneSetting.showMaskerPane(() -> {
            boolean success = reportExportService.exportMonthlyTotalsReport(
                    tableView.getItems(),
                    textStart.getText() + " - " + year,
                    file.getAbsolutePath()
            );

            javafx.application.Platform.runLater(() -> {
                if (success) {
                    AllAlerts.alertSaveWithMessage("تم التصدير بنجاح" +
                            "تم حفظ التقرير في:\n" + file.getAbsolutePath());
                } else {
                    AllAlerts.alertError("حدث خطأ أثناء التصدير");
                }
            });
//            });
        }
    }

    private void getData() {
        maskerPaneSetting.showMaskerPane(() -> {
            List<TableTotals> list = new ArrayList<>();
            if (reportTotalsInterface.searchByNames()) {
                // add data to table
                HashMap<Integer, String> map = reportTotalsInterface.mapNames();
                map.forEach((integer, string) -> list.add(tableTotals(integer, string)));
            } else {
                List<Integer> list1 = reportTotalsInterface.listByYearOrDay()
                        .stream().sorted().toList();
                for (Integer o : list1) {
                    list.add(tableTotals(o, String.valueOf(o)));
                }
            }
            list.add(getE());
            tableTotals.clear();
            tableTotals.addAll(list);
        });

    }

    private TableTotals getE() {
        try {
            return new TableTotals(Setting_Language.WORD_TOTAL
                    , reportTotalsInterface.sumColumnTotal(1), reportTotalsInterface.sumColumnTotal(2), reportTotalsInterface.sumColumnTotal(3)
                    , reportTotalsInterface.sumColumnTotal(4), reportTotalsInterface.sumColumnTotal(5), reportTotalsInterface.sumColumnTotal(6)
                    , reportTotalsInterface.sumColumnTotal(7), reportTotalsInterface.sumColumnTotal(8), reportTotalsInterface.sumColumnTotal(9)
                    , reportTotalsInterface.sumColumnTotal(10), reportTotalsInterface.sumColumnTotal(11), reportTotalsInterface.sumColumnTotal(12)
                    , 1);
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
        }
        return new TableTotals();
    }

    private TableTotals tableTotals(int o, String n) {
        TableTotals tableTotals = new TableTotals();
        tableTotals.setName(String.valueOf(n));
        try {
            tableTotals.setJan(reportTotalsInterface.sumColumn(1, year, o));
            tableTotals.setFeb(reportTotalsInterface.sumColumn(2, year, o));
            tableTotals.setMar(reportTotalsInterface.sumColumn(3, year, o));
            tableTotals.setApril(reportTotalsInterface.sumColumn(4, year, o));
            tableTotals.setMay(reportTotalsInterface.sumColumn(5, year, o));
            tableTotals.setJun(reportTotalsInterface.sumColumn(6, year, o));
            tableTotals.setJuly(reportTotalsInterface.sumColumn(7, year, o));
            tableTotals.setAug(reportTotalsInterface.sumColumn(8, year, o));
            tableTotals.setSep(reportTotalsInterface.sumColumn(9, year, o));
            tableTotals.setOct(reportTotalsInterface.sumColumn(10, year, o));
            tableTotals.setNov(reportTotalsInterface.sumColumn(11, year, o));
            tableTotals.setDes(reportTotalsInterface.sumColumn(12, year, o));
            tableTotals.setTotals(reportTotalsInterface.sumColumn(0, year, o));
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
        }
        return tableTotals;
    }

}
