package com.hamza.account.view;

import com.hamza.account.backup.BackupService;
import com.hamza.account.config.ConnectionToDatabase;
import com.hamza.account.config.Style_Sheet;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.service.*;
import com.hamza.controlsfx.alert.AlertSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.util.FontsSetting;
import javafx.application.Application;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import static com.hamza.account.backup.ScheduledBackup.ENCRYPTION_PASSWORD;

@Log4j2
public class DownLoadApplication extends Application {

    private static ConnectionToDatabase connectionToDatabase;
    private final DaoFactory daoFactory;

    public DownLoadApplication() {
        FontsSetting.fontName(FontsSetting.EL_MESSIRI);
        FontsSetting.fontName(FontsSetting.GRAND_HOTEL);
        FontsSetting.fontName(FontsSetting.NEW_ROCKER);
        FontsSetting.fontName(FontsSetting.GAFATA);

        // change language
        connectionToDatabase = new ConnectionToDatabase();
        daoFactory = getDaoFactory();
        AlertSetting.stylesheetPath = Style_Sheet.getStyle();

        System.out.println(1);
        ServiceRegistry.register(ItemsService.class, new ItemsService(daoFactory));
        ServiceRegistry.register(StockService.class, new StockService(daoFactory));
        ServiceRegistry.register(StockTransferService.class, new StockTransferService(daoFactory));
        ServiceRegistry.register(StockTransferListService.class, new StockTransferListService(daoFactory));
        ServiceRegistry.register(EmployeeService.class, new EmployeeService(daoFactory));
        ServiceRegistry.register(TreasuryService.class, new TreasuryService(daoFactory));
        ServiceRegistry.register(UnitsService.class, new UnitsService(daoFactory));
        ServiceRegistry.register(UsersService.class, new UsersService(daoFactory));
        ServiceRegistry.register(MainGroupService.class, new MainGroupService(daoFactory));
        ServiceRegistry.register(SupGroupService.class, new SupGroupService(daoFactory));
        ServiceRegistry.register(TreasuryTransferService.class, new TreasuryTransferService(daoFactory));
        ServiceRegistry.register(CardItemService.class, new CardItemService(daoFactory));
        ServiceRegistry.register(TargetDetailsService.class, new TargetDetailsService(daoFactory));
        ServiceRegistry.register(TargetService.class, new TargetService(daoFactory));
        ServiceRegistry.register(ExpensesService.class, new ExpensesService(daoFactory));
        ServiceRegistry.register(ExpensesDetailsService.class, new ExpensesDetailsService(daoFactory));
        ServiceRegistry.register(TotalSalesService.class, new TotalSalesService(daoFactory));
        ServiceRegistry.register(TotalBuyService.class, new TotalBuyService(daoFactory));
        ServiceRegistry.register(TotalBuyReturnService.class, new TotalBuyReturnService(daoFactory));
        ServiceRegistry.register(TotalSalesReturnService.class, new TotalSalesReturnService(daoFactory));
        ServiceRegistry.register(SalesService.class, new SalesService(daoFactory));
        ServiceRegistry.register(EarningsService.class, new EarningsService(daoFactory));
        ServiceRegistry.register(CustomerService.class, new CustomerService(daoFactory));
        ServiceRegistry.register(SuppliersService.class, new SuppliersService(daoFactory));
        ServiceRegistry.register(AccountCustomerService.class, new AccountCustomerService(daoFactory));
        ServiceRegistry.register(AccountSupplierService.class, new AccountSupplierService(daoFactory));
        ServiceRegistry.register(AuditLogService.class, new AuditLogService(daoFactory));
        ServiceRegistry.register(DepositService.class, new DepositService(daoFactory));
        ServiceRegistry.register(TreasuryBalanceService.class, new TreasuryBalanceService(daoFactory));
        ServiceRegistry.register(ItemMiniQuantityService.class, new ItemMiniQuantityService(daoFactory));
        ServiceRegistry.register(AreaService.class, new AreaService(daoFactory));
        ServiceRegistry.register(SelPriceItemService.class, new SelPriceItemService(daoFactory));
        ServiceRegistry.register(ItemPackageService.class, new ItemPackageService(daoFactory));
        ServiceRegistry.register(UserShiftService.class, new UserShiftService(daoFactory));

        System.out.println(2);
    }

    public static void main(String[] args) {
        launch(args);
    }

    public static DaoFactory getDaoFactory() {
        try {
            DaoFactory daoFactory = DaoFactory.INSTANCE;
            var connection = connectionToDatabase.getDbConnection().getConnection();
            daoFactory.setConnection(connection);
//            new TrialManager(connection).checkTrialStatus();
            return daoFactory;
        } catch (DaoException e) {
            AllAlerts.alertError(e.getMessage());
            System.exit(0);
        }
        return null;
    }

    public static BackupService loadBackupService() {
        var connection = connectionToDatabase;
        return new BackupService(connection.getHost()
                , connection.getPort(), connection.getDbName(), connection.getUsername(), connection.getPass()
                , ENCRYPTION_PASSWORD);
    }

    @Override
    public void start(Stage stage) throws Exception {
        new LogApplication(daoFactory).start(new Stage());
    }


}
