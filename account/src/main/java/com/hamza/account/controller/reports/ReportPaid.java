package com.hamza.account.controller.reports;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.interfaces.api.AccountData;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.filechooser.ImageChoose;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
@FxmlPath(pathFile = "reports/report-paid.fxml")
public class ReportPaid<T extends BaseAccount> {
    private final AccountData<T> accountData;
    private final String title;
    @FXML
    private DatePicker dateFrom, dateTo;
    @FXML
    private Button btnSearch, btnPrint;
    @FXML
    private StackPane stackPane;
    @FXML
    private Text textCount, textTotal;
    @FXML
    private Label labelFrom, labelTo,labelCount,labelTotal,textTitle;
    @FXML
    private TableView<T> tableView;

    @FXML
    public void initialize() {
        log.info("ReportPaid initialized");
        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);
        getTable();
        otherSetting();
    }

    private void getTable() {
        new TableColumnAnnotation().getTable(tableView, BaseAccount.class);
        accountData.updateTableView(tableView);
        TableSetting.tableMenuSetting(getClass(), tableView);

        TableColumn col2 = tableView.getColumns().get(0);
        TableColumn col3 = tableView.getColumns().get(1);
        TableColumn col5 = tableView.getColumns().get(4);
        TableColumn col6 = tableView.getColumns().get(6);
        tableView.getColumns().retainAll(col2, col3, col5, col6);
    }

    private void otherSetting() {
        textTitle.setText(title);
        var imageSetting = new Image_Setting();
        btnSearch.setGraphic(ImageChoose.createIcon(imageSetting.search));
        btnPrint.setGraphic(ImageChoose.createIcon(imageSetting.print));

        labelCount.setText(Setting_Language.WORD_COUNT);
        labelTotal.setText(Setting_Language.WORD_TOTAL);
        labelFrom.setText(Setting_Language.WORD_FROM);
        labelTo.setText(Setting_Language.WORD_TO);
        btnPrint.setText(Setting_Language.WORD_PRINT);
        btnSearch.setText(Setting_Language.WORD_SEARCH);

        btnSearch.setOnAction(actionEvent -> {
            try {
                searchTable();
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        });

        btnPrint.setOnAction(actionEvent -> printTable());
    }

    private void searchTable() throws Exception {
        ObservableList<T> list = FXCollections.observableArrayList();
        if (dateFrom.getValue() == null || dateTo.getValue() == null) {
            log.error("Date from and date to must be selected");
            AllAlerts.alertError(Error_Text_Show.NOT_POSSIBLE);
            return;
        }
        list.addAll(accountData.getAccountBetweenDate(dateFrom.getValue().toString(), dateTo.getValue().toString()));
        tableView.setItems(list);
        textCount.setText(String.valueOf(list.size()));
        textTotal.setText(String.valueOf(list.stream().mapToDouble(BaseAccount::getPaid).sum()));
    }

    private void printTable() {
        //TODO 11/16/2025 9:15 AM Mohamed: add print
        AllAlerts.alertError("لم يتم عمل التقرير");
    }

}
