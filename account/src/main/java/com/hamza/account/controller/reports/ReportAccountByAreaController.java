package com.hamza.account.controller.reports;

import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.model.domain.Area;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.service.AccountCustomerService;
import com.hamza.account.service.AreaService;
import com.hamza.account.service.CustomerService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.Column;
import com.hamza.controlsfx.table.TreeTable;
import com.hamza.controlsfx.table.colorRow.RowColor;
import com.hamza.controlsfx.table.colorRow.RowColorInterface;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableView;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Deprecated
@Log4j2
@FxmlPath(pathFile = "reports/report-account-by-area.fxml")
public class ReportAccountByAreaController {

    private final AreaService areaService;
    private final AccountCustomerService accountCustomerService;
    private final CustomerService customerService;
    @FXML
    private TreeTableView<TreeArea> treeTableView;
    @FXML
    private Button btnShowAll, btnPrint;

    public ReportAccountByAreaController(ServiceData serviceData) {
        this.areaService = serviceData.getAreaService();
        this.accountCustomerService = serviceData.getAccountCustomerService();
        this.customerService = serviceData.getCustomerService();
    }

    @FXML
    public void initialize() {
        treeTableView.getColumns().clear();
        TreeTable.createTable(treeTableView, initializeAccountColumnDefinitions());
        treeTableView.getColumns().getFirst().setPrefWidth(100);
        treeTableView.getColumns().get(1).setPrefWidth(200);
        addArea();
        action();

        new RowColor().customiseRowInTree(treeTableView, new RowColorInterface<>() {
            @Override
            public boolean checkRow(TreeArea t1) {
                return t1.getId() != 0;
            }
        });
    }

    private List<Column<?>> initializeAccountColumnDefinitions() {
        return new ArrayList<>(Arrays.asList(
                new Column<>(Integer.class, "id", Setting_Language.WORD_NUM),
                new Column<>(String.class, "name", Setting_Language.WORD_NAME),
                new Column<>(Double.class, "purchase", Setting_Language.PURCHASE),
                new Column<>(Double.class, "sale", Setting_Language.WORD_PAID),
                new Column<>(Double.class, "amount", Setting_Language.WORD_REST)
        ));
    }

    private void addArea() {
        var customerAccounts = accountCustomerService.accountTotalList(null, null);
        var sumPurchase = customerAccounts.stream().mapToDouble(CustomerAccount::getPurchase).sum();
        var sumPaid = customerAccounts.stream().mapToDouble(CustomerAccount::getPaid).sum();
        var sumAmount = customerAccounts.stream().mapToDouble(CustomerAccount::getAmount).sum();
        TreeItem<TreeArea> treeItem = new TreeItem<>(new TreeArea(1, Setting_Language.AREA, sumPurchase, sumPaid, sumAmount));
        treeTableView.setRoot(treeItem);
        treeItem.setExpanded(true);
//        addIconToTreeItems(treeItem, Color.RED);
        var areas = getAreas();
        for (Area area : areas) {
            var list = customerAccounts.stream().filter(customerAccount -> customerAccount.getArea_id() == area.getId()).toList();
            var sum1 = list.stream().mapToDouble(CustomerAccount::getPurchase).sum();
            var sum2 = list.stream().mapToDouble(CustomerAccount::getPaid).sum();
            var sum3 = list.stream().mapToDouble(CustomerAccount::getAmount).sum();
            var e = new TreeItem<>(new TreeArea(area.getId(), area.getArea_name(), sum1, sum2, sum3));
//            e.setExpanded(true);
//            addIconToTreeItems(e, Color.BLUE);
            treeItem.getChildren().add(e);
            for (CustomerAccount customerAccount : list) {
                var e1 = new TreeItem<>(new TreeArea(0, customerAccount.getCustomers().getName(), customerAccount.getPurchase(), customerAccount.getPaid(), customerAccount.getAmount()));
                e1.setExpanded(true);
                e.getChildren().add(e1);
            }
        }
    }

    private List<Area> getAreas() {
        try {
            return areaService.fetchAllAreas();
        } catch (DaoException e) {
            log.error(e.getMessage());
            return List.of();
        }
    }

    private void action() {
        btnShowAll.setOnAction(event -> {
            treeTableView.setRoot(new TreeItem<>(new TreeArea(1, Setting_Language.AREA, 0, 0, 0)));
//            treeTableView.setShowRoot(false);
            addArea();
        });

        btnPrint.setOnAction(event -> {
            if (treeTableView.getRoot() != null) {
                var treeItem = treeTableView.getRoot();
                if (!treeItem.getChildren().isEmpty()) {
                    var list = accountCustomerService.accountTotalList(null, null)
                            .stream()
                            .sorted(Comparator.comparingInt(CustomerAccount::getArea_id)).toList();
                    new Print_Reports().printAccountsByArea(list);
                }
            }
        });
    }

}


