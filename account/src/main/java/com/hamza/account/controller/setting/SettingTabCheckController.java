package com.hamza.account.controller.setting;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.view.LogApplication;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.text.Text;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.net.URL;
import java.util.ResourceBundle;

import static com.hamza.account.config.PropertiesName.*;

@Log4j2
@FxmlPath(pathFile = "include/settingTabChecks.fxml")
@RequiredArgsConstructor
public class SettingTabCheckController implements Initializable {

    private final DataPublisher dataPublisher;

    @FXML
    private CheckBox checkBalance, checkShowImageHint;
    @FXML
    private CheckBox checkSelWithoutBalance;
    @FXML
    private CheckBox checkShowColumnSelectedInItems;
    @FXML
    private CheckBox checkValidity;
    @FXML
    private CheckBox checkShowBeforePrint;
    @FXML
    private CheckBox updatePriceInInvoice, printReceiptInvoice;
    @FXML
    private CheckBox checkIncreaseItemOnTable;
    @FXML
    private CheckBox checkBackupAfterSave;
    @FXML
    private CheckBox checkPrintReceiptAccount, checkAddItemDirect;
    @FXML
    private CheckBox checkLogin, checkShowTotals, checkInvoicePaid, showScreenAlone;
    @FXML
    private Text textInvoice, textItem, textOthers;


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        otherSetting();
        forItems();
    }

    private void otherSetting() {
        textItem.setText(Setting_Language.WORD_ITEMS);
        textInvoice.setText(Setting_Language.showInv);
        textOthers.setText(Setting_Language.OTHERS);
    }

    private void forItems() {
        checkLogin.setDisable(LogApplication.usersVo.getId() != 1);
        checkShowTotals.setDisable(LogApplication.usersVo.getId() != 1);
        checkSetting(checkShowBeforePrint, "عرض قبل الطباعة", getPrintPaperDirect());
        checkSetting(checkPrintReceiptAccount, "طباعة الحساب طابعة حرارية", getPrintPaperReceiptAccount());

        checkSetting(printReceiptInvoice, Setting_Language.PRINT_RECEIPT_INVOICE, getPrintPaperReceiptInvoice());
        checkSetting(updatePriceInInvoice, Setting_Language.UPDATE_PRICE_FROM_INVOICE, getInvoiceUpdatePrice());
        checkSetting(checkBackupAfterSave, "نسخة إحتياطية بعد حفظ الفاتورة", getInvoiceBackupAfterSave());

        checkSetting(checkBalance, "عرض تنبيه الاصناف", getItemShowAlert());

        checkSetting(checkShowImageHint, "إظهار تلميحات الصورة", getItemImageHint());
        checkSetting(checkAddItemDirect, "إضافة الصنف مباشرة فى الفاتورة", getInvoiceAddItemDirect());

        checkSetting(checkIncreaseItemOnTable, "جمع الاصناف المكررة", getInvoiceIncreaseItemOneTable());
        checkSetting(checkSelWithoutBalance, Setting_Language.BUY_WITHOUT_BALANCE, getSelWithoutBalance());

        checkSetting(checkLogin, "إظهار شاشة الدخول", getSettingLoginShow());
        checkSetting(checkShowTotals, "عرض الاجماليات فى الشاشة الرئيسية", getShowMainTotals());
        checkSetting(checkInvoicePaid, "إظهار شاشة الدفع فى الفواتير", getInvoiceShowScreenPaid());

        checkSetting(showScreenAlone, "عرض شاشة منفصلة", getSettingShowInvoiceScreenSeparate());

        checkInvoicePaid.selectedProperty().addListener((observable, oldValue, newValue) -> setInvoiceShowScreenPaid(newValue));

        checkLogin.selectedProperty().addListener((observable, oldValue, newValue) -> {
            dataPublisher.getShowLoginScreen().publish(newValue);
            setSettingLoginShow(newValue);
        });
        checkShowTotals.selectedProperty().addListener((observable, oldValue, newValue) -> {
            dataPublisher.getShowMainTotalsScreen().publish(newValue);
            setShowMainTotals(newValue);
        });

        showScreenAlone.selectedProperty().addListener((observable, oldValue, newValue) -> setSettingShowInvoiceScreenSeparate(newValue));
        checkShowBeforePrint.selectedProperty().addListener((observable, oldValue, newValue) -> setPrintPaperDirect(newValue));
        checkPrintReceiptAccount.selectedProperty().addListener((observable, oldValue, newValue) -> setPrintPaperReceiptAccount(newValue));
        printReceiptInvoice.selectedProperty().addListener((observable, oldValue, newValue) -> setPrintPaperReceiptInvoice(newValue));
        updatePriceInInvoice.selectedProperty().addListener((observable, oldValue, newValue) -> setInvoiceUpdatePrice(newValue));
        checkBackupAfterSave.selectedProperty().addListener((observable, oldValue, newValue) -> setInvoiceBackupAfterSave(newValue));
        checkBalance.selectedProperty().addListener((observable, oldValue, newValue) -> setItemShowAlert(newValue));


        checkShowImageHint.selectedProperty().addListener((observable, oldValue, newValue) -> setItemImageHint(newValue));
        checkIncreaseItemOnTable.selectedProperty().addListener((observable, oldValue, newValue) -> setInvoiceIncreaseItemOneTable(newValue));
        checkSelWithoutBalance.selectedProperty().addListener((observable, oldValue, newValue) -> setSelWithoutBalance(newValue));
        checkAddItemDirect.selectedProperty().addListener((observableValue, aBoolean, t1) -> setInvoiceAddItemsDirect(t1));
    }

    private void checkSetting(CheckBox checkBox, String nameText, boolean b) {
        checkBox.setText(nameText);
        checkBox.setSelected(b);
    }
}
