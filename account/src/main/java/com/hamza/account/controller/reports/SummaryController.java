package com.hamza.account.controller.reports;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.DailyDashboardReport;
import com.hamza.account.model.domain.TopSellingItem;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@Log4j2
@FxmlPath(pathFile = "reports/summary.fxml")
public class SummaryController implements AppSettingInterface {

    private final String textName;
    private final DailyDashboardReport dailyDashboardReport;
    private final List<TopSellingItem> topSellingItems;
    private double netSales, netPurchase;
    @FXML
    private Label labelDay, labelPreviousDay, labelWeek, labelMonth;
    @FXML
    private Label amount;
    @FXML
    private Label cash1, cash2, cash3, cash4, cash5; // for cash
    @FXML
    private Label countPurchase, countPurchaseRe, countSales, countSalesRe; // for count
    @FXML
    private Label totalPurchase, totalPurchaseCash, totalPurchaseDefer, totalPurchaseDiscount, totalPurchaseRe; //for purchase
    @FXML
    private Label totalSales, totalSalesCash, totalSalesDefer, totalSalesDiscount, totalSalesRe; // for sales
    @FXML
    private Label totalPurchaseReDiscount, totalSalesReDiscount;
    @FXML
    private TableView<TopSellingItem> tableView;
    @FXML
    private StackPane stackPane;

    public SummaryController(DaoFactory daoFactory, String textName) throws Exception {
        this.textName = textName;
        var dailyDashboardReportDao = daoFactory.dailyDashboardReportDao();
        dailyDashboardReport = dailyDashboardReportDao.loadAll().stream().findFirst().orElse(null);

        var topSellingItemDao = daoFactory.topSellingItemDao();
        topSellingItems = topSellingItemDao.loadAll();
    }

    @FXML
    public void initialize() {
        getTableItems();
        MaskerPaneSetting maskerPaneSetting = new MaskerPaneSetting(stackPane);
        maskerPaneSetting.showMaskerPane(() -> tableView.setItems(FXCollections.observableArrayList(topSellingItems)));

        labelDay.setText(String.valueOf(dailyDashboardReport.getSalesTotalToday()));
        labelPreviousDay.setText(String.valueOf(dailyDashboardReport.getSalesTotalYesterday()));
        labelWeek.setText(String.valueOf(dailyDashboardReport.getSalesTotalWeek()));
        labelMonth.setText(String.valueOf(dailyDashboardReport.getSalesTotalMonth()));

    }


    private void getTableItems() {
        // 2. إنشاء عمود "اسم الصنف"
        TableColumn<TopSellingItem, String> nameCol = new TableColumn<>("اسم الصنف");
        nameCol.setMinWidth(200);
        // "itemName" يجب أن تتطابق تماماً مع اسم المتغير في الكلاس TopSellingItem
        nameCol.setCellValueFactory(new PropertyValueFactory<>("itemName"));

        // 3. إنشاء عمود "الكمية"
        TableColumn<TopSellingItem, BigDecimal> quantityCol = new TableColumn<>("إجمالي الكمية");
        quantityCol.setMinWidth(150);
        quantityCol.setCellValueFactory(new PropertyValueFactory<>("totalQuantity"));

        // 4. إنشاء عمود "متوسط السعر"
        TableColumn<TopSellingItem, BigDecimal> priceCol = new TableColumn<>("متوسط السعر");
        priceCol.setMinWidth(150);
        priceCol.setCellValueFactory(new PropertyValueFactory<>("averagePrice"));

        // 5. إضافة الأعمدة إلى الجدول
        tableView.getColumns().addAll(nameCol, quantityCol, priceCol);
    }

    @Override
    public Pane pane() throws IOException {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        return textName;
    }

    @Override
    public boolean resize() {
        return true;
    }
}

