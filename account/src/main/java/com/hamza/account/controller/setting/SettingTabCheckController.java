package com.hamza.account.controller.setting;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.view.LogApplication;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.controlsfx.dialog.FontSelectorDialog;

import java.net.URL;
import java.util.ResourceBundle;

import static com.hamza.account.config.PropertiesName.*;
import static com.hamza.account.config.PropertiesName.getInvoiceAddItemDirect;
import static com.hamza.account.config.PropertiesName.setInvoiceAddItemsDirect;

@Log4j2
@FxmlPath(pathFile = "include/settingTabChecks.fxml")
@RequiredArgsConstructor
public class SettingTabCheckController implements Initializable {

    private final DataPublisher dataPublisher;

    @FXML
    private CheckBox checkPrintTitleInReports;
    @FXML
    private CheckBox checkBalance,checkEditItems, checkShowImageHint;
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
    private CheckBox checkPrintReceiptAccount, checkPosSelectPrice,checkAddItemDirect;
    @FXML
    private CheckBox checkInsideDatabase;
    @FXML
    private CheckBox checkLogin, checkShowTotals, checkInvoicePaid, checkServer, showScreenAlone;
    @FXML
    private Text textInvoice, textItem, textOthers;
    @FXML
    private TextField textHeight, textWidth, textHeightMenu, textWidthMenu;
    @FXML
    private Spinner<Integer> spinnerFontName, spinnerFontPrice;
    @FXML
    private Button btnChooseFont, btnChooseFontPrice;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        otherSetting();
        forItems();
        initializeSpinners();

        //TODO 11/22/2025 5:56 PM Mohamed: check in realtime
        checkEditItems.setDisable(true);
    }

    private void initializeSpinners() {
        spinnerFontName.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 12));
        spinnerFontPrice.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 12));
        spinnerFontName.getValueFactory().setValue(getPosInvoiceFontNameSize());
        spinnerFontPrice.getValueFactory().setValue(getPosInvoiceFontPriceSize());
        spinnerFontName.valueProperty().addListener((observable, oldValue, newValue) -> setPosInvoiceFontNameSize(newValue));
        spinnerFontPrice.valueProperty().addListener((observable, oldValue, newValue) -> setPosInvoiceFontPriceSize(newValue));

        btnChooseFont.setOnAction(e -> {
            FontSelectorDialog dialog = new FontSelectorDialog(Font.getDefault());
            dialog.setTitle("Choose Font");
            dialog.showAndWait().ifPresent(font -> {
                // Handle selected font
                setPosInvoiceFontNameSize((int) font.getSize());
                spinnerFontName.getValueFactory().setValue((int) font.getSize());
            });
        });
        btnChooseFontPrice.setOnAction(actionEvent -> {
            FontSelectorDialog dialog = new FontSelectorDialog(Font.getDefault());
            dialog.setTitle("Choose Font");
            dialog.showAndWait().ifPresent(font -> {
                // Handle selected font
                setPosInvoiceFontPriceSize((int) font.getSize());
                spinnerFontPrice.getValueFactory().setValue((int) font.getSize());
            });
        });

    }

    private void otherSetting() {
        textItem.setText(Setting_Language.WORD_ITEMS);
        textInvoice.setText(Setting_Language.showInv);
        textOthers.setText(Setting_Language.OTHERS);

        textHeight.setText(String.valueOf(getPosInvoiceItemsSizeHeight()));
        textWidth.setText(String.valueOf(getPosInvoiceItemsSizeWidth()));
        textHeightMenu.setText(String.valueOf(getMenuSearchSizeHeight()));
        textWidthMenu.setText(String.valueOf(getMenuSearchSizeWidth()));

        textHeight.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.matches("\\d*")) {
                setPosInvoiceItemsSizeHeight(Integer.parseInt(newValue));
            } else {
                textHeight.setText(oldValue);
            }
        });
        textWidth.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.matches("\\d*")) {
                setPosInvoiceItemsSizeWidth(Integer.parseInt(newValue));
            } else {
                textWidth.setText(oldValue);
            }
        });

        textHeightMenu.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.matches("\\d*")) {
                setMenuSearchSizeHeight(Integer.parseInt(newValue));
            } else {
                textHeightMenu.setText(oldValue);
            }
        });
        textWidthMenu.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.matches("\\d*")) {
                setMenuSearchSizeWidth(Integer.parseInt(newValue));
            } else {
                textWidthMenu.setText(oldValue);
            }
        });

    }

    private void forItems() {
        checkLogin.setDisable(LogApplication.usersVo.getId() != 1);
        checkShowTotals.setDisable(LogApplication.usersVo.getId() != 1);
        checkSetting(checkShowBeforePrint, "عرض قبل الطباعة", getPrintPaperDirect());
        checkSetting(checkPrintReceiptAccount, "طباعة الحساب طابعة حرارية", getPrintPaperReceiptAccount());
        checkSetting(checkInsideDatabase, "قاعدة بيانات محلية", getDatabaseUsePathVariableSetting());
        checkSetting(printReceiptInvoice, Setting_Language.PRINT_RECEIPT_INVOICE, getPrintPaperReceiptInvoice());
        checkSetting(updatePriceInInvoice, Setting_Language.UPDATE_PRICE_FROM_INVOICE, getInvoiceUpdatePrice());
        checkSetting(checkBackupAfterSave, "نسخة إحتياطية بعد حفظ الفاتورة", getInvoiceBackupAfterSave());

        checkSetting(checkBalance, "عرض تنبيه الاصناف", getItemShowAlert());
        checkSetting(checkEditItems, "تعديل الاصناف من الجدول", getItemEditFromTable());
        checkSetting(checkShowImageHint, "إظهار تلميحات الصورة", getItemImageHint());
        checkSetting(checkPosSelectPrice, "إظهار اختيار السعر فى الفواتير POS", getPosInvoiceShowSelectPrice());
        checkSetting(checkAddItemDirect, "إضافة الصنف مباشرة فى الفاتورة", getInvoiceAddItemDirect());

        checkSetting(checkIncreaseItemOnTable, "جمع الاصناف المكررة", getInvoiceIncreaseItemOneTable());
        checkSetting(checkSelWithoutBalance, Setting_Language.BUY_WITHOUT_BALANCE, getSelWithoutBalance());
        checkSetting(checkPrintTitleInReports, Setting_Language.PRINT_HEADER, getSettingPrintReportTitle());
        checkSetting(checkLogin, "إظهار شاشة الدخول", getSettingLoginShow());
        checkSetting(checkShowTotals, "عرض الاجماليات فى الشاشة الرئيسية", getShowMainTotals());
        checkSetting(checkInvoicePaid, "إظهار شاشة الدفع فى الفواتير", getInvoiceShowScreenPaid());
        checkSetting(checkServer, "يعمل كسيرفر", getSettingServerStart());
        checkSetting(showScreenAlone, "عرض شاشة منفصلة", getSettingShowInvoiceScreenSeparate());

        checkInvoicePaid.selectedProperty().addListener((observable, oldValue, newValue) -> setInvoiceShowScreenPaid(newValue));
        checkServer.selectedProperty().addListener((observable, oldValue, newValue) -> setSettingServerStart(newValue));
        checkLogin.selectedProperty().addListener((observable, oldValue, newValue) -> {
            dataPublisher.getShowLoginScreen().setAvailability(newValue);
            setSettingLoginShow(newValue);
        });
        checkShowTotals.selectedProperty().addListener((observable, oldValue, newValue) -> {
            dataPublisher.getShowMainTotalsScreen().setAvailability(newValue);
            setShowMainTotals(newValue);
        });

        showScreenAlone.selectedProperty().addListener((observable, oldValue, newValue) -> setSettingShowInvoiceScreenSeparate(newValue));
        checkShowBeforePrint.selectedProperty().addListener((observable, oldValue, newValue) -> setPrintPaperDirect(newValue));
        checkPrintReceiptAccount.selectedProperty().addListener((observable, oldValue, newValue) -> setPrintPaperReceiptAccount(newValue));
        checkInsideDatabase.selectedProperty().addListener((observable, oldValue, newValue) -> setDatabaseUsePathVariableSetting(newValue));
        printReceiptInvoice.selectedProperty().addListener((observable, oldValue, newValue) -> setPrintPaperReceiptInvoice(newValue));
        updatePriceInInvoice.selectedProperty().addListener((observable, oldValue, newValue) -> setInvoiceUpdatePrice(newValue));
        checkBackupAfterSave.selectedProperty().addListener((observable, oldValue, newValue) -> setInvoiceBackupAfterSave(newValue));
        checkBalance.selectedProperty().addListener((observable, oldValue, newValue) -> setItemShowAlert(newValue));
        checkEditItems.selectedProperty().addListener((observable, oldValue, newValue) -> setItemEditFromTable(newValue));
        checkShowImageHint.selectedProperty().addListener((observable, oldValue, newValue) -> setItemImageHint(newValue));
        checkIncreaseItemOnTable.selectedProperty().addListener((observable, oldValue, newValue) -> setInvoiceIncreaseItemOneTable(newValue));
        checkSelWithoutBalance.selectedProperty().addListener((observable, oldValue, newValue) -> setSelWithoutBalance(newValue));
        checkPrintTitleInReports.selectedProperty().addListener((observable, oldValue, newValue) -> setSettingPrintReportTitle(newValue));
        checkPosSelectPrice.selectedProperty().addListener((observable, oldValue, newValue) -> setPosInvoiceShowSelectPrice(newValue));
        checkAddItemDirect.selectedProperty().addListener((observableValue, aBoolean, t1) -> setInvoiceAddItemsDirect(t1));
    }

    private void checkSetting(CheckBox checkBox, String nameText, boolean b) {
        checkBox.setText(nameText);
        checkBox.setSelected(b);
    }
}
