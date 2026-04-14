package com.hamza.account.controller.reports;

import com.hamza.account.controller.others.SearchDateController;
import com.hamza.account.controller.model.DayModel;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Total_Sales;
import com.hamza.account.view.OpenApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.dateTime.SearchByDate;
import com.hamza.controlsfx.dateTime.SearchInTwoDate;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.table.Table_Setting;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.text.Text;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Log4j2
public class DayDetailsTableController {

    private final DaoFactory daoFactory;
    @FXML
    private SearchDateController searchDateController;
    @FXML
    private TableView<DayModel> tableView;
    @FXML
    private Button search;
    @FXML
    private Text txtTotalSales, txtTotalPaid;

    public DayDetailsTableController(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
    }

    @FXML
    private void initialize() {
        tableSetting();
        action();
        sumData();
    }

    private void tableSetting() {
        new TableColumnAnnotation().getTable(tableView, DayModel.class);
        ObservableList<DayModel> list = FXCollections.observableArrayList(listTable());
        tableView.setItems(list);
        tableView.getColumns().addFirst(Table_Setting.column_number());
    }

    private List<DayModel> listTable() {
        ArrayList<DayModel> dayModels = new ArrayList<>();
        try {
            List<Total_Sales> totalSales = daoFactory.totalsSalesDao().loadAll();
            List<CustomerAccount> customerAccounts = daoFactory.customerAccountDao().loadAll();

            List<String> listDate1 = totalSales.stream().map(Total_Sales::getDate).toList();
            List<String> listDate2 = customerAccounts.stream().map(CustomerAccount::getDate).toList();

            List<String> newList = Stream.concat(listDate1.stream(), listDate2.stream()).distinct().sorted().toList();
            for (String s : newList) {
                double sales = totalSales.stream().filter(totalSales1 -> totalSales1.getDate().equals(s)).mapToDouble(Total_Sales::getTotal).sum();
                double paid = customerAccounts.stream().filter(totalSales1 -> totalSales1.getDate().equals(s)).mapToDouble(CustomerAccount::getPaid).sum();
                DayModel build = DayModel.builder()
                        .dateInsert(s)
                        .sales(sales)
                        .paid(paid)
                        .build();
                dayModels.add(build);
            }
        } catch (DaoException e) {
            log.error(e.getMessage());
        }

        return dayModels;
    }

    private List<DayModel> searchDate() throws Exception {
        return SearchInTwoDate.searchInDate(new SearchByDate<>() {
            @Override
            public String getDate(DayModel dayModel) {
                return dayModel.getDateInsert();
            }

            @Override
            public String firstDate() {
                return searchDateController.getDateFrom().getValue().toString();
            }

            @Override
            public String lastDate() {
                return searchDateController.getDateTo().getValue().toString();
            }

            @Override
            public List<DayModel> list() {
                return listTable();
            }
        });
    }

    private void action() {

        // button search action
        search.setOnAction(actionEvent -> {
            try {
                tableView.setItems(FXCollections.observableArrayList(searchDate()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        tableView.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) {
                if (tableView.getSelectionModel().getSelectedItem() != null) {
                    try {
                        new OpenApplication<>(new DayDetailsTreeController(tableView.getSelectionModel().getSelectedItem().getDateInsert(), daoFactory));
                    } catch (Exception e) {
                        log.error(e.getMessage(), e.getCause() + " - " + this.getClass().getName());
                        AllAlerts.alertError(e.getMessage());
                    }
                }
            }

        });
        tableView.itemsProperty().addListener((observableValue, dayModels, t1) -> sumData());
    }

    private void sumData() {
        txtTotalSales.setText((String.valueOf(tableView.getItems().stream().mapToDouble(DayModel::getSales).sum())));
        txtTotalPaid.setText((String.valueOf(tableView.getItems().stream().mapToDouble(DayModel::getPaid).sum())));
    }
}
