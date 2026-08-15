package com.hamza.account.view;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.backup.BackupService;
import com.hamza.account.backup.ScheduledBackup;
import com.hamza.account.config.ConnectionToDatabase;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.config.UiScale;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.company.CompanyService;
import com.hamza.account.features.inventory.InventoryService;
import com.hamza.account.features.notification.NotificationBootstrap;
import com.hamza.account.features.rbac.JdbcRbacRepository;
import com.hamza.account.features.rbac.RbacService;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.stockcount.StockCountService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.period.PeriodLockService;
import com.hamza.account.service.*;
import com.hamza.account.service.version.DatabaseMigrationService;
import com.hamza.account.service.version.MigrationResult;
import com.hamza.account.trial.TrialManager;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DataSourceProvider;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.others.ChangeOrientation;
import com.hamza.controlsfx.util.FontsSetting;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.SQLException;

@Log4j2
public class DownLoadApplication extends Application {

    private static ConnectionToDatabase connectionToDatabase;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        loadFonts();
        UiScale.initialize();
        ThemeManager.initialize();

        LanguageManager language = LanguageManager.getInstance();
        Label status = new Label(language.getString("startup.preparing"));
        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(56, 56);
        Button close = new Button(language.getString("common.close"));
        close.setVisible(false);
        close.setManaged(false);
        close.setOnAction(event -> Platform.exit());

        VBox root = new VBox(18, progress, status, close);
        root.setAlignment(Pos.CENTER);
        Scene scene = new Scene(root, 420, 260);
        ThemeManager.apply(scene);
        ChangeOrientation.sceneOrientation(scene);

        primaryStage.setTitle(language.getString("startup.title"));
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.setOnCloseRequest(event -> Platform.exit());
        primaryStage.show();

        Task<BootstrapResult> bootstrapTask = new Task<>() {
            @Override
            protected BootstrapResult call() {
                updateMessage(language.getString("startup.preparing"));
                return bootstrap();
            }
        };
        status.textProperty().bind(bootstrapTask.messageProperty());
        bootstrapTask.setOnSucceeded(event -> {
            status.textProperty().unbind();
            BootstrapResult result = bootstrapTask.getValue();
            showMigrationResult(result.migrationResult());

            ApplicationNavigator navigator = new ApplicationNavigator(primaryStage, result.daoFactory());
            ServiceRegistry.register(ApplicationNavigator.class, navigator);
            navigator.showLogin();
        });
        bootstrapTask.setOnFailed(event -> {
            status.textProperty().unbind();
            progress.setVisible(false);
            progress.setManaged(false);
            status.setText(language.getString("startup.failed"));
            close.setVisible(true);
            close.setManaged(true);
            AllAlerts.handleError(language.getString("startup.operation"), bootstrapTask.getException());
        });

        Thread thread = new Thread(bootstrapTask, "application-bootstrap");
        thread.setDaemon(true);
        thread.start();
    }

    private BootstrapResult bootstrap() {
        connectionToDatabase = new ConnectionToDatabase();
        MigrationResult migration = new DatabaseMigrationService(connectionToDatabase).updateDatabaseIfNeeded();
        DaoFactory daoFactory = getDaoFactory();
        checkTrialStatus();
        registerServices(daoFactory);
        return new BootstrapResult(daoFactory, migration);
    }

    private static void registerServices(DaoFactory daoFactory) {
        ServiceRegistry.register(EventBus.class, new EventBus());

        UserSessionContext userSession = new UserSessionContext();
        JdbcRbacRepository authorizationRepository = new JdbcRbacRepository();
        try {
            authorizationRepository.synchronizeCatalog(AppPermissions.definitions());
        } catch (DaoException e) {
            throw new IllegalStateException("Could not synchronize the permission catalog", e);
        }
        ServiceRegistry.register(UserSessionContext.class, userSession);
        ServiceRegistry.register(RbacService.class, new RbacService(authorizationRepository, userSession));

        ServiceRegistry.register(CompanyService.class, new CompanyService(daoFactory));
        ServiceRegistry.register(ItemsService.class, new ItemsService(daoFactory));
        ServiceRegistry.register(StockService.class, new StockService(daoFactory));
        ServiceRegistry.register(InventoryService.class, new InventoryService(daoFactory));
        ServiceRegistry.register(StockCountService.class, new StockCountService(daoFactory));
        ServiceRegistry.register(PeriodLockService.class, new PeriodLockService(daoFactory));
        ServiceRegistry.register(EmployeeService.class, new EmployeeService(daoFactory));
        ServiceRegistry.register(TreasuryService.class, new TreasuryService(daoFactory));
        ServiceRegistry.register(UnitsService.class, new UnitsService(daoFactory));
        ServiceRegistry.register(UsersService.class, new UsersService(daoFactory));
        ServiceRegistry.register(MainGroupService.class, new MainGroupService(daoFactory));
        ServiceRegistry.register(SupGroupService.class, new SupGroupService(daoFactory));
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

    private static void showMigrationResult(MigrationResult result) {
        if (!result.isUpdated()) return;

        LanguageManager language = LanguageManager.getInstance();
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(language.getString("startup.database.updated.title"));
        alert.setHeaderText(language.getString("startup.database.updated.header"));
        alert.setContentText(language.getString("startup.database.updated.details",
                result.getOldVersion(), result.getRequiredVersion(), result.getExecutedVersions().size()));
        ThemeManager.apply(alert.getDialogPane());
        ChangeOrientation.sceneOrientation(alert.getDialogPane().getScene());
        alert.showAndWait();
    }

    public static DaoFactory getDaoFactory() {
        if (connectionToDatabase == null) throw new IllegalStateException("Database configuration is not initialized");
        try (var connection = connectionToDatabase.getDbConnection().getConnection()) {
            if (!connection.isValid(5)) throw new DaoException("Database connection validation failed");
            return DaoFactory.INSTANCE;
        } catch (DaoException | SQLException e) {
            throw new IllegalStateException("Could not connect to the database", e);
        }
    }

    private static void checkTrialStatus() {
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            new TrialManager(connection).checkTrialStatus();
        } catch (SQLException e) {
            log.error("Could not verify the trial status", e);
        } finally {
            ConnectionManager.release(connection);
        }
    }

    public static BackupService loadBackupService() {
        if (connectionToDatabase == null) throw new IllegalStateException("Database configuration is not initialized");
        return new BackupService(connectionToDatabase.getHost(), connectionToDatabase.getPort(),
                connectionToDatabase.getDbName(), connectionToDatabase.getUsername(),
                connectionToDatabase.getPass(), ScheduledBackup.encryptionPassword());
    }

    @Override
    public void stop() {
        NotificationBootstrap.stop();
        ScheduledBackup.stopScheduler();
        DataSourceProvider.shutdown();
    }

    private static void loadFonts() {
        FontsSetting.fontName(FontsSetting.EL_MESSIRI);
        FontsSetting.fontName(FontsSetting.GRAND_HOTEL);
        FontsSetting.fontName(FontsSetting.NEW_ROCKER);
        FontsSetting.fontName(FontsSetting.GAFATA);
    }

    private record BootstrapResult(DaoFactory daoFactory, MigrationResult migrationResult) {
    }
}
