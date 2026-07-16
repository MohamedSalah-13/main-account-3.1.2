package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.invoice.BuyController2;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class BuyApplication<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount> extends Application {

    // قاموس لتخزين الـ Stages المفتوحة: المفتاح هو رقم الفاتورة والقيمة هي الـ Stage
    private static final Map<Integer, Stage> openInvoices = new HashMap<>();

    private final Pane pane;
    private final BuyController2<T1, T2, T3, T4> controller;
    private final int numInvoiceUpdate;
    private String title = "Buy";

    public BuyApplication(DataInterface<T1, T2, T3, T4> dataInterface, DataPublisher dataPublisher
            , int numInvoiceUpdate) throws Exception {
        controller = new BuyController2<>(dataInterface, dataPublisher, numInvoiceUpdate);
        pane = controller.pane();
        this.numInvoiceUpdate = numInvoiceUpdate;
        title = dataInterface.designInterface().nameTextOfInvoice();
    }

    @Override
    public void start(Stage stage) throws Exception {

        // --- الجزء الجديد: التحقق من رقم الفاتورة ---
        if (numInvoiceUpdate != 0) { // نفترض أن 0 تعني فاتورة جديدة وليس تعديل
            Stage existingStage = openInvoices.get(numInvoiceUpdate);

            if (existingStage != null && existingStage.isShowing()) {
                existingStage.toFront();      // إحضارها للمقدمة
                existingStage.requestFocus(); // التركيز عليها
                return;                       // الخروج وعدم إظهار نافذة جديدة
            }

            // إذا كانت موجودة لكنها مغلقة/مخفية، نحذفها
            openInvoices.remove(numInvoiceUpdate);

            // إذا لم تكن مفتوحة، نضيفها للقائمة
            openInvoices.put(numInvoiceUpdate, stage);
        }

        Scene scene = new SceneAll(pane);
        stage.setScene(scene);
        stage.setTitle(title);
        stage.setResizable(true);
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().tools));

        // حذف رقم الفاتورة عند اختفاء النافذة بأي طريقة
        stage.setOnHidden(event -> {
            if (numInvoiceUpdate != 0) {
                openInvoices.remove(numInvoiceUpdate);
            }
        });

        stage.show();
//        Screen_Size.adjustStageToTwoThirdsScreen(stage);

        var btnSave = getController().getBtnSave();

        btnSave.setText(btnSave.getText() + "F10");
        btnSave.getScene().getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F10),
                btnSave::fire
        );

        var btnPrintSave = getController().getBtnPrintSave();
        btnPrintSave.setText(btnSave.getText() + "F12");
        btnPrintSave.getScene().getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F12),
                btnPrintSave::fire
        );

    }
}