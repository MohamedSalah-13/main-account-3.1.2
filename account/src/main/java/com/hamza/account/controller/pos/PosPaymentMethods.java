package com.hamza.account.controller.pos;

import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.others.Utils;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import static com.hamza.account.config.PropertiesName.getPosPrintCustomer;
import static com.hamza.account.config.PropertiesName.setPosPrintCustomer;

public class PosPaymentMethods extends Dialog<InvoiceData> {
    private final RadioButton cashPayment = new RadioButton("نقدا");
    private final TextField payerField = new TextField();
    private final CheckBox printCustomer = new CheckBox("طباعة بيانات العميل");
    private final CheckBox printToKitchen = new CheckBox("طباعة فاتورة المطبخ");
    private final CheckBox printInvoice = new CheckBox("طباعة الفاتورة");

    public PosPaymentMethods() {
        setResizable(false);

        var paymentMethod = "طريقة الدفع";
        setTitle(paymentMethod);
        setHeaderText(paymentMethod);

        payerField.setPrefWidth(300);
        payerField.setMinWidth(300);
        Utils.setTextFormatter(payerField);
        ToggleGroup paymentGroup = new ToggleGroup();
        cashPayment.setToggleGroup(paymentGroup);
        RadioButton deferPayment = new RadioButton("أجل");
        deferPayment.setToggleGroup(paymentGroup);
        cashPayment.setSelected(true);

        VBox content = new VBox(10);
        HBox paymentBox = new HBox(10, cashPayment, deferPayment);
        HBox payerBox = new HBox(10, new Label("المدفوع"), payerField);
        VBox vBox = new VBox(10, printCustomer, printToKitchen, printInvoice);

        content.getChildren().addAll(paymentBox, payerBox, vBox);
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL);

        DialogButtons.changeNameAndGraphic(getDialogPane());

        setResultConverter(dialogButton -> {
            if (dialogButton == javafx.scene.control.ButtonType.OK) {
                var invoiceData = new InvoiceData();
                if (cashPayment.isSelected()) {
                    invoiceData.setInvoiceType(InvoiceType.CASH);
                } else {
                    invoiceData.setInvoiceType(InvoiceType.DEFER);
                }
                invoiceData.setPaid(Double.parseDouble(payerField.getText()));
                invoiceData.setPrintCustomer(printCustomer.isSelected());
                invoiceData.setPrintToKitchen(printToKitchen.isSelected());
                invoiceData.setPrintInvoice(printInvoice.isSelected());
                return invoiceData;
            }
            return null;
        });

        payerField.disableProperty().bind(cashPayment.selectedProperty());
        checkSetting();
    }

    private void checkSetting() {
        printCustomer.setSelected(getPosPrintCustomer());
        printCustomer.selectedProperty().addListener((observable, oldValue, newValue) ->
                setPosPrintCustomer(newValue));

        printToKitchen.setSelected(true);
        printInvoice.setSelected(true);
    }

    public boolean isCashPayment() {
        return cashPayment.isSelected();
    }

    public String getPayer() {
        return payerField.getText();
    }
}

