package com.hamza.account.controller.setting;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.controlsfx.language.LanguageManager;
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
    private CheckBox checkShowTotals, checkInvoicePaid, showScreenAlone;
    @FXML
    private Text textInvoice, textItem, textOthers;


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        otherSetting();
        forItems();
    }

    private void otherSetting() {
        var lm = LanguageManager.getInstance();
        textItem.setText(lm.getString("items"));
        textInvoice.setText(lm.getString("settings.checks.invoiceSection"));
        textOthers.setText(lm.getString("others"));
    }

    private void forItems() {
        var lm = LanguageManager.getInstance();
        checkShowTotals.setDisable(CurrentUser.get().getId() != 1);
        checkSetting(checkShowBeforePrint, lm.getString("settings.checks.showBeforePrint"), getPrintPaperDirect());
        checkSetting(checkPrintReceiptAccount, lm.getString("settings.checks.printAccountThermal"), getPrintPaperReceiptAccount());

        checkSetting(printReceiptInvoice, lm.getString("settings.checks.printReceiptInvoice"), getPrintPaperReceiptInvoice());
        checkSetting(updatePriceInInvoice, lm.getString("settings.checks.updatePriceInInvoice"), getInvoiceUpdatePrice());
        checkSetting(checkBackupAfterSave, lm.getString("settings.checks.backupAfterSave"), getInvoiceBackupAfterSave());

        checkSetting(checkBalance, lm.getString("settings.checks.itemAlert"), getItemShowAlert());

        checkSetting(checkShowImageHint, lm.getString("settings.checks.showImageHint"), getItemImageHint());
        checkSetting(checkAddItemDirect, lm.getString("settings.checks.addItemDirect"), getInvoiceAddItemDirect());

        checkSetting(checkIncreaseItemOnTable, lm.getString("settings.checks.mergeDuplicateItems"), getInvoiceIncreaseItemOneTable());
        checkSetting(checkSelWithoutBalance, lm.getString("settings.checks.sellWithoutBalance"), getSelWithoutBalance());

        checkSetting(checkShowTotals, lm.getString("settings.checks.showTotalsOnHome"), getShowMainTotals());
        checkSetting(checkInvoicePaid, lm.getString("settings.checks.showPaidScreen"), getInvoiceShowScreenPaid());

        checkSetting(showScreenAlone, lm.getString("settings.checks.showSeparateScreen"), getSettingShowInvoiceScreenSeparate());

        checkInvoicePaid.selectedProperty().addListener((observable, oldValue, newValue) -> setInvoiceShowScreenPaid(newValue));

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
