package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.invoice.BuyController2;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.interfaces.api.DataInterface;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class BuyApplication extends Application {

    // قاموس لتخزين الـ Stages المفتوحة: المفتاح هو رقم الفاتورة والقيمة هي الـ Stage
    private static final Map<Integer, Stage> openInvoices = new HashMap<>();

    private final Pane pane;
    private final BuyController2<?, ?, ?, ?> controller;
    private final int numInvoiceUpdate;
    private String title = "Buy";

    public BuyApplication(DataInterface<?, ?, ?, ?> dataInterface, DataPublisher dataPublisher
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
            if (openInvoices.containsKey(numInvoiceUpdate)) {
                Stage existingStage = openInvoices.get(numInvoiceUpdate);
                existingStage.toFront();    // إحضارها للمقدمة
                existingStage.requestFocus(); // التركيز عليها
                return; // الخروج وعدم إظهار نافذة جديدة
            }
            // إذا لم تكن مفتوحة، نضيفها للقائمة
            openInvoices.put(numInvoiceUpdate, stage);
        }

        Scene scene = new SceneAll(pane);
        stage.setScene(scene);
        stage.setTitle(title);
        stage.setResizable(true);
        stage.getIcons().add(new javafx.scene.image.Image(new Image_Setting().tools));

        // --- الجزء الجديد: مسح رقم الفاتورة من القائمة عند إغلاق النافذة ---
        stage.setOnCloseRequest(event -> {
            if (numInvoiceUpdate != 0) {
                openInvoices.remove(numInvoiceUpdate);
            }
        });

        stage.show();
//        StageDimensions.stageDimensions(getClass(), stage);

        var btnSave = getController().getBtnSave();

        btnSave.setText(btnSave.getText() + " F10");
        btnSave.getScene().getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F10),
                btnSave::fire
        );

        var btnPrintSave = getController().getBtnPrintSave();
        btnPrintSave.setText("حفظ وطباعة F12");
        btnPrintSave.getScene().getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F12),
                btnPrintSave::fire
        );

    }
}