package com.hamza.account.test;

import com.hamza.account.config.Style_Sheet;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadDataAndList;
import com.hamza.account.controller.others.DeleteDataController;
import com.hamza.account.controller.reports.ReportPaid;
import com.hamza.account.controller.reports.model.TableTotals;
import com.hamza.account.features.export.ReportExportService;
import com.hamza.account.interfaces.impl_account.AccountCustomer;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.view.DownLoadApplication;
import com.hamza.account.view.LogApplication;
import com.hamza.account.view.OpenApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.view.PassCheckApplication;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Log4j2
public class Tests extends Application {

    private static DaoFactory daoFactory = DownLoadApplication.getDaoFactory();
    private DataPublisher dataPublisher;

    public static void main(String[] args) throws Exception {
//        LicenseKey.loadLicenseFile(new File("0cb2a2cadbf6e3bb82ee44cc18579426b158f1cd9342d247408dbf677b1bf958.json"));
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
//        LoadDataAndList loadDataAndList = new LoadDataAndList(daoFactory);
//        loadDataAndList.run();
//        LogApplication.usersVo = daoFactory.usersDao().getDataById(1);
//        dataPublisher = new DataPublisher();

//        TextField itemField = new TextField();
//        itemField.setPromptText("ابحث عن صنف...");
//
//        List<Customers> customersList = LoadDataAndList.getListCustomers();
//
//        AutoCompletionBinding<Customers> customerBinding = TextFields.bindAutoCompletion(
//                itemField,
//                customersList // Simple way to show customer name
//        );
//
//        customerBinding.setOnAutoCompleted(event -> {
//            Customers selected = event.getCompletion();
//            log.info("Selected item: " + selected.getName() + " (ID: " + selected.getId() + ")");
//        });
//        updateItems(DataList.getItemsModelList());
        var pane = new OpenFxmlApplication(new UpdateController()).getPane();
//        var stackPane = new ItemsControllerTree(daoFactory, dataPublisher).getStackPane();
        Scene scene = new Scene(pane, 600, 400);
        stage.setScene(scene);
        stage.show();

//        var customData = new CustomData(daoFactory, dataPublisher);
//        int id =2;
//        var customerById = customData.getCustomerService().getCustomerById(id);
//        AccountDetailsWithItemsController accountDetailsController = new AccountDetailsWithItemsController<>(daoFactory, dataPublisher
//                , customData, customerById.getName(), id, null);
//        new OpenApplication<>(accountDetailsController);
//        exportToPdf();

//        var itemsModelList = new ArrayList<ItemsModel>();
//        itemsModelList.add(new ItemsModel(1, "item 1"));
//        new ConvertItemsGroup(daoFactory, itemsModelList).start(new Stage());
    }

    private void exportToPdf() {
        String textStart = "report";
        String year = "2025";
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("حفظ التقرير");
        fileChooser.setInitialFileName(textStart + "_" + year + ".pdf");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );

        File file = fileChooser.showSaveDialog(null);

        if (file != null) {
            List<TableTotals> tableTotals = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                tableTotals.add(new TableTotals("f", 1, 2, 3, 4, 55, 6, 7, 8, 9, 10, 11, 12, 15));
            }

//            maskerPaneSetting.showMaskerPane(() -> {
            var reportExportService = new ReportExportService();
            boolean success = reportExportService.exportMonthlyTotalsReport(
                    FXCollections.observableArrayList(tableTotals),
                    textStart + " - " + year,
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

    private void paas() throws Exception {
        var check = new PassCheckApplication("147852369");
        DialogPane dialogPane = check.getDialogPane();
        var scene = dialogPane.getScene();
        Style_Sheet.changeStyle(scene);
        LoadDataAndList loadDataAndList = new LoadDataAndList(daoFactory);

        Optional<Boolean> optionalS = check.showAndWait();
        optionalS.ifPresent(aBoolean -> {
            if (aBoolean) {
                try {
                    Stage stage = new Stage();
                    stage.setTitle("");
                    new OpenApplication<>(new DeleteDataController(daoFactory, dataPublisher, loadDataAndList));
                } catch (Exception e) {
                    AllAlerts.alertError(e.getMessage());
                }
            } else AllAlerts.alertError(Setting_Language.THE_PASSWORD_IS_INCORRECT);
        });
    }
}