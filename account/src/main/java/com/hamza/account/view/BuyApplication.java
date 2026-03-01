package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.PropertiesName;
import com.hamza.account.controller.invoice.BuyController2;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.table.StageDimensions;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

@Getter
public class BuyApplication<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount> extends Application {

    private final Set<Integer> openInvoices = new HashSet<>();
    private final Pane pane;
    private final BuyController2<T1, T2, T3, T4> controller;
    private final int numInvoiceUpdate;
    private String title = "Buy";

    public BuyApplication(DataInterface<T1, T2, T3, T4> dataInterface, DaoFactory daoFactory
            , DataPublisher dataPublisher
            , int numInvoiceUpdate) throws Exception {
        controller = new BuyController2<>(dataInterface, daoFactory, dataPublisher, numInvoiceUpdate);
        pane = controller.pane();
        this.numInvoiceUpdate = numInvoiceUpdate;
        title = dataInterface.designInterface().nameTextOfInvoice();
    }

    @Override
    public void start(Stage stage) throws Exception {
//        if (!openInvoices.add(numInvoiceUpdate)) {
//            throw new IllegalStateException("Invoice #" + numInvoiceUpdate + " is already open");
//        }

        Scene scene = new SceneAll(pane);
        stage.setScene(scene);
        stage.setTitle(title);
        stage.setResizable(true);
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().tools));
        stage.setOnCloseRequest(e -> openInvoices.remove(numInvoiceUpdate));
        stage.show();
        StageDimensions.stageDimensions(getClass(), stage);

        if (PropertiesName.getSettingShowInvoiceScreenSeparate() || numInvoiceUpdate > 0) {
            KeyCodeCombination KEY_BTN_PRINT_SAVE = new KeyCodeCombination(KeyCode.F12);
            KeyCodeCombination KEY_BTN_SAVE = new KeyCodeCombination(KeyCode.F10);

            var btnPrintSave = controller.getBtnPrintSave();
            btnPrintSave.setText(btnPrintSave.getText() + " (F12)");
            btnPrintSave.setTooltip(new javafx.scene.control.Tooltip(btnPrintSave.getText()));
            var btnSave = controller.getBtnSave();
            btnSave.setText(btnSave.getText() + " (F10)");
            btnSave.setTooltip(new javafx.scene.control.Tooltip(btnSave.getText()));


            scene.getAccelerators().put(KEY_BTN_PRINT_SAVE, btnPrintSave::fire);
            scene.getAccelerators().put(KEY_BTN_SAVE, btnSave::fire);
        }
    }
}
