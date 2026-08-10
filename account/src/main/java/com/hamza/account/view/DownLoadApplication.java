package com.hamza.account.view;

import com.hamza.account.backup.BackupService;
import com.hamza.account.config.ConnectionToDatabase;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.service.*;
import com.hamza.account.service.version.DatabaseMigrationService;
import com.hamza.account.service.version.MigrationResult;
import com.hamza.account.trial.TrialManager;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.util.FontsSetting;
import javafx.application.Application;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.SQLException;
import lombok.extern.log4j.Log4j2;

import com.hamza.account.backup.ScheduledBackup;

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
        // Must stay ahead of getDaoFactory(): the DAOs assume the current schema, and on a new
        // machine there is no database to connect to until this has created it.
        updateDatabaseIfNeeded();
        daoFactory = getDaoFactory();
        checkTrialStatus();
//        AlertSetting.setStylesheets(ThemeManager.getStylesheet());
        ThemeManager.initialize();

        // Registered first, and without a DaoFactory: screens pull it from here
        // instead of being handed a publisher through their constructor.
        ServiceRegistry.register(EventBus.class, new EventBus());

        ServiceRegistry.register(ItemsService.class, new ItemsService(daoFactory));
        ServiceRegistry.register(StockService.class, new StockService(daoFactory));
        ServiceRegistry.register(EmployeeService.class, new EmployeeService(daoFactory));
        ServiceRegistry.register(TreasuryService.class, new TreasuryService(daoFactory));
        ServiceRegistry.register(UnitsService.class, new UnitsService(daoFactory));
        ServiceRegistry.register(UsersService.class, new UsersService(daoFactory));
        ServiceRegistry.register(MainGroupService.class, new MainGroupService(daoFactory));
        ServiceRegistry.register(SupGroupService.class, new SupGroupService(daoFactory));
        ServiceRegistry.register(TreasuryTransferService.class, new TreasuryTransferService(daoFactory));
        ServiceRegistry.register(CardItemService.class, new CardItemService(daoFactory));
        ServiceRegistry.register(ExpensesService.class, new ExpensesService(daoFactory));
        ServiceRegistry.register(ExpensesDetailsService.class, new ExpensesDetailsService(daoFactory));
        ServiceRegistry.register(TotalSalesService.class, new TotalSalesService(daoFactory));
        ServiceRegistry.register(TotalBuyService.class, new TotalBuyService(daoFactory));
        ServiceRegistry.register(TotalBuyReturnService.class, new TotalBuyReturnService(daoFactory));
        ServiceRegistry.register(TotalSalesReturnService.class, new TotalSalesReturnService(daoFactory));
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
        ServiceRegistry.register(UserShiftService.class, new UserShiftService(daoFactory));

        ServiceRegistry.register(PurchaseService.class, new PurchaseService(daoFactory));
        ServiceRegistry.register(PurchaseReService.class, new PurchaseReService(daoFactory));
        ServiceRegistry.register(SalesService.class, new SalesService(daoFactory));
        ServiceRegistry.register(SalesReService.class, new SalesReService(daoFactory));


    }

    public static void main(String[] args) {
        launch(args);
    }

    private void updateDatabaseIfNeeded() {
        try {
            MigrationResult result = new DatabaseMigrationService().updateDatabaseIfNeeded();

            if (result.isUpdated()) {
                log.info("Database updated from {} to {}. Executed migrations: {}",
                        result.getOldVersion(), result.getRequiredVersion(), result.getExecutedVersions());

                String message = "من الإصدار: %s\nإلى الإصدار: %s\nعدد التحديثات: %d"
                        .formatted(result.getOldVersion(), result.getRequiredVersion(),
                                result.getExecutedVersions().size());

                var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setTitle("تحديث قاعدة البيانات");
                alert.setHeaderText("تم تحديث قاعدة البيانات بنجاح");
                alert.setContentText(message);
                alert.showAndWait();
            }
        } catch (Exception e) {
            log.error("Database update failed on startup", e);
            AllAlerts.alertError("تعذر تحديث قاعدة البيانات، لا يمكن فتح البرنامج:\n" + e.getMessage());
            System.exit(0);
        }
    }

    /**
     * This used to borrow a connection from the pool and hand it to the DaoFactory
     * to keep forever. The DAOs now take a connection per call, so all that is
     * needed here is to confirm the database is reachable before the UI opens -
     * the connection this opens is returned to the pool immediately.
     */
    public static DaoFactory getDaoFactory() {
        try (var connection = connectionToDatabase.getDbConnection().getConnection()) {
            if (!connection.isValid(5)) {
                throw new DaoException("تعذر الاتصال بقاعدة البيانات");
            }
            return DaoFactory.INSTANCE;
        } catch (DaoException | SQLException e) {
            AllAlerts.alertError(e.getMessage());
            System.exit(0);
        }
        return null;
    }

    /**
     * Enforces the trial before the UI opens. A valid license.dat short-circuits
     * the whole check, so a licensed install never reaches the trial rules.
     * <p>
     * The per-record limits in the DAOs - items, customers, sales, purchases -
     * were being enforced the whole time this was switched off, which left an
     * unlicensed copy running forever but capped at ten items. Running the check
     * again puts the time limit back alongside them.
     */
    private static void checkTrialStatus() {
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            new TrialManager(connection).checkTrialStatus();
        } catch (SQLException e) {
            // Reachability was just confirmed by getDaoFactory, so this is not the
            // database being down. TrialManager swallows its own errors rather than
            // locking the user out of a program they may have paid for, and this
            // follows it.
            log.error("Could not verify the trial status", e);
        } finally {
            ConnectionManager.release(connection);
        }
    }

    public static BackupService loadBackupService() {
        var connection = connectionToDatabase;
        return new BackupService(connection.getHost()
                , connection.getPort(), connection.getDbName(), connection.getUsername(), connection.getPass()
                , ScheduledBackup.encryptionPassword());
    }

    @Override
    public void start(Stage stage) throws Exception {
        new LogApplication(daoFactory).start(new Stage());
    }


}
