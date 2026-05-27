package com.hamza.account.controller.main;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.security.PermissionHelper;
import com.hamza.account.type.PermissionCode;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.text.Text;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import org.controlsfx.control.PopOver;
import javafx.scene.control.ListView;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.input.MouseEvent;

import java.io.InputStream;
import java.net.URL;
import java.util.ResourceBundle;

import static com.hamza.controlsfx.language.Setting_Language.*;

@Log4j2
public class MainRightPaneController implements Initializable {

    @FXML
    private TextField txtSearch;
    @Getter
    @FXML
    private Button btnSales, btnTotalSale, btnPurchase, btnTotalPurchase, btnPurchaseRe, btnTotalPurchaseRe, btnItems,
            btnAddItem, btnUnits, btnMainGroup, btnInventory, btnCustomer, btnAccountCustom, btnSuppliers,
            btnAccountSuppliers,
            btnAddDeposit, btnTreasuryDetails, btnConvertTreasury, btnProcess, btnExpenses, btnHome, btnUsers, btnBackup, btnClose;
    @FXML
    private TitledPane paneSales, panePurchase, paneItems, paneCustom, paneSuppliers, paneTreasury, paneSetting;
    @FXML
    private Text txtNameProject, txtName, txtTel;
    private PopOver searchPopOver;
    private ListView<String> searchResultsListView;
    // يمكنك استبدال String بكلاس مخصص (مثلاً SearchResult) إذا أردت إرجاع كود العنصر مع اسمه

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        otherSetting();
        setupGlobalSearch();


        // ✅ تطبيق الصلاحيات على أزرار القائمة الجانبية
        applyPermissions();
    }

    /**
     * ✅ تطبيق الصلاحيات على جميع الأزرار
     */
    private void applyPermissions() {
        // المبيعات
        PermissionHelper.hideIfNotAllowed(btnSales, PermissionCode.TOTAL_SALES_CREATE);
        PermissionHelper.hideIfNotAllowed(btnTotalSale, PermissionCode.TOTAL_SALES_SHOW);

        // المشتريات
        PermissionHelper.hideIfNotAllowed(btnPurchase, PermissionCode.TOTAL_PURCHASE_CREATE);
        PermissionHelper.hideIfNotAllowed(btnTotalPurchase, PermissionCode.TOTAL_PURCHASE_SHOW);
        PermissionHelper.hideIfNotAllowed(btnPurchaseRe, PermissionCode.TOTAL_PURCHASE_RE_CREATE);
        PermissionHelper.hideIfNotAllowed(btnTotalPurchaseRe, PermissionCode.TOTAL_PURCHASE_RE_SHOW);

        // الأصناف
        PermissionHelper.hideIfNotAllowed(btnItems, PermissionCode.ITEMS_SHOW);
        PermissionHelper.hideIfNotAllowed(btnAddItem, PermissionCode.ITEMS_CREATE);
        PermissionHelper.hideIfNotAllowed(btnUnits, PermissionCode.UNITS_SHOW);
        PermissionHelper.hideIfNotAllowed(btnMainGroup, PermissionCode.MAIN_GROUP_SHOW);
        PermissionHelper.hideIfNotAllowed(btnInventory, PermissionCode.STOCK_ADJUSTMENT);

        // العملاء
        PermissionHelper.hideIfNotAllowed(btnCustomer, PermissionCode.CUSTOMERS_SHOW);
        PermissionHelper.hideIfNotAllowed(btnAccountCustom, PermissionCode.CUSTOMERS_ACCOUNT_SHOW);

        // الموردين
        PermissionHelper.hideIfNotAllowed(btnSuppliers, PermissionCode.SUPPLIERS_SHOW);
        PermissionHelper.hideIfNotAllowed(btnAccountSuppliers, PermissionCode.SUPPLIERS_ACCOUNT_SHOW);

        // الخزائن
        PermissionHelper.hideIfNotAllowed(btnAddDeposit, PermissionCode.TREASURY_DEPOSIT);
        PermissionHelper.hideIfNotAllowed(btnTreasuryDetails, PermissionCode.TREASURY_SHOW);
        PermissionHelper.hideIfNotAllowed(btnConvertTreasury, PermissionCode.TREASURY_TRANSFER);
        PermissionHelper.hideIfNotAllowed(btnProcess, PermissionCode.TREASURY_WITHDRAW);
        PermissionHelper.hideIfNotAllowed(btnExpenses, PermissionCode.EXPENSES_SHOW);

        // الإعدادات
        // btnHome - متاح للجميع
        PermissionHelper.hideIfNotAllowed(btnUsers, PermissionCode.USERS_SHOW);
        PermissionHelper.hideIfNotAllowed(btnBackup, PermissionCode.SETTINGS_BACKUP);
        // btnClose - متاح للجميع

        // إخفاء/إظهار TitledPane بناءً على محتوياتها
        applyTitledPaneVisibility();

        log.info("تم تطبيق الصلاحيات على القائمة الجانبية");
    }

    /**
     * إخفاء TitledPane إذا كانت جميع أزرارها مخفية
     */
    private void applyTitledPaneVisibility() {
        checkAndHidePaneIfEmpty(paneSales);
        checkAndHidePaneIfEmpty(panePurchase);
        checkAndHidePaneIfEmpty(paneItems);
        checkAndHidePaneIfEmpty(paneCustom);
        checkAndHidePaneIfEmpty(paneSuppliers);
        checkAndHidePaneIfEmpty(paneTreasury);
        checkAndHidePaneIfEmpty(paneSetting);
    }

    /**
     * التحقق من وجود أزرار مرئية في الـ TitledPane
     */
    private void checkAndHidePaneIfEmpty(TitledPane pane) {
        if (pane.getContent() instanceof javafx.scene.layout.Pane contentPane) {
            boolean hasVisibleButton = contentPane.getChildren().stream()
                    .filter(node -> node instanceof Button)
                    .anyMatch(node -> node.isVisible());

            if (!hasVisibleButton) {
                pane.setVisible(false);
                pane.setManaged(false);
            }
        }
    }

    private void otherSetting() {
        var imageSetting = new Image_Setting();
        txtSearch.setPromptText(Setting_Language.WORD_SEARCH);
        titlePaneSetting(paneSales, Setting_Language.WORD_SALES, imageSetting.shoppingSales);
        titlePaneSetting(panePurchase, Setting_Language.WORD_PUR, imageSetting.shoppingPurchase);
        titlePaneSetting(paneItems, Setting_Language.WORD_ITEMS, imageSetting.itemWhite);
        titlePaneSetting(paneCustom, Setting_Language.WORD_CUSTOM, imageSetting.personCustomer);
        titlePaneSetting(paneSuppliers, Setting_Language.WORD_SUP, imageSetting.personSup);
        titlePaneSetting(paneTreasury, Setting_Language.TREASURY, imageSetting.treasuryWhite);
        titlePaneSetting(paneSetting, Setting_Language.WORD_SETTING, imageSetting.setting);

        // Add a listener to txtSearch to filter TitledPane buttons
        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
//            filterTitledPaneButtons(newValue);
        });

        txtNameProject.setText(PROGRAM_TITLE);
        txtName.setText(PROGRAM_NAME_EN);
        txtTel.setText(PROGRAM_TEL);
    }


    private void titlePaneSetting(TitledPane titledPane, String text, InputStream stream) {
        titledPane.setText(text);
        titledPane.setGraphic(new ImageDesign(stream, 20));
    }

    private void filterTitledPaneButtons(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            // إرجاع القائمة لحالتها الطبيعية إذا كان مربع البحث فارغاً
            resetSearchVisibility();
            return;
        }

        // تحويل نص البحث إلى حروف صغيرة لضمان دقة البحث
        String lowerCaseSearchText = searchText.toLowerCase();

        // تطبيق الفلترة على كل قائمة (TitledPane)
        filterSpecificPane(paneSales, lowerCaseSearchText);
        filterSpecificPane(panePurchase, lowerCaseSearchText);
        filterSpecificPane(paneItems, lowerCaseSearchText);
        filterSpecificPane(paneCustom, lowerCaseSearchText);
        filterSpecificPane(paneSuppliers, lowerCaseSearchText);
        filterSpecificPane(paneTreasury, lowerCaseSearchText);
        filterSpecificPane(paneSetting, lowerCaseSearchText);
    }

    private void filterSpecificPane(TitledPane titledPane, String searchText) {
        // هل عنوان القائمة الرئيسية يطابق البحث؟
        boolean paneTitleMatches = titledPane.getText() != null && titledPane.getText().toLowerCase().contains(searchText);
        boolean hasMatchingButton = false;

        // التأكد من أن محتوى القائمة هو Pane (مثل VBox) لنتمكن من المرور على الأزرار داخله
        if (titledPane.getContent() instanceof javafx.scene.layout.Pane contentPane) {

            for (javafx.scene.Node node : contentPane.getChildren()) {
                if (node instanceof Button button) {
                    boolean buttonMatches = button.getText() != null && button.getText().toLowerCase().contains(searchText);

                    // إظهار الزر إذا كان يطابق البحث، أو إذا كان عنوان القائمة الرئيسية نفسه يطابق البحث
                    if (buttonMatches || paneTitleMatches) {
                        button.setVisible(true);
                        button.setManaged(true);
                        hasMatchingButton = true;
                    } else {
                        // إخفاء الأزرار التي لا تتطابق مع البحث
                        button.setVisible(false);
                        button.setManaged(false);
                    }
                }
            }
        }

        // إظهار الـ TitledPane فقط إذا كان عنوانه يطابق أو إذا كان بداخله زر يطابق
        boolean shouldShowPane = paneTitleMatches || hasMatchingButton;
        setTitledPaneVisibility(titledPane, shouldShowPane);

        // فتح القائمة تلقائياً (Expand) لتسهيل رؤية الزر المطابق
        if (shouldShowPane) {
            titledPane.setExpanded(true);
        }
    }

    private void resetSearchVisibility() {
        // مصفوفة بكل القوائم لإرجاعها لوضعها الافتراضي
        TitledPane[] allPanes = {paneSales, panePurchase, paneItems, paneCustom, paneSuppliers, paneTreasury, paneSetting};

        for (TitledPane pane : allPanes) {
            setTitledPaneVisibility(pane, true);
            pane.setExpanded(false); // يمكنك تغييرها لـ true إذا كنت تفضل أن تكون القوائم مفتوحة دائماً

            // إرجاع جميع الأزرار لتكون مرئية
            if (pane.getContent() instanceof javafx.scene.layout.Pane contentPane) {
                for (javafx.scene.Node node : contentPane.getChildren()) {
                    if (node instanceof Button) {
                        node.setVisible(true);
                        node.setManaged(true);
                    }
                }
            }
        }
    }

    private void setTitledPaneVisibility(TitledPane titledPane, boolean visible) {
        titledPane.setVisible(visible);
        titledPane.setManaged(visible);
    }

    private void setupGlobalSearch() {
        // 1. إعداد قائمة عرض النتائج (ListView)
        searchResultsListView = new ListView<>();
        searchResultsListView.setPrefSize(250, 200); // عرض وارتفاع نافذة النتائج

        // 2. إعداد نافذة الـ PopOver المنبثقة
        searchPopOver = new PopOver(searchResultsListView);
        searchPopOver.setArrowLocation(PopOver.ArrowLocation.TOP_CENTER); // السهم يشير للأعلى نحو مربع البحث
        searchPopOver.setDetachable(false); // منع المستخدم من فصلها كنافذة حرة
        searchPopOver.setHeaderAlwaysVisible(false); // إخفاء العنوان العلوي للنافذة

        // 3. مراقبة ما يكتبه المستخدم في مربع البحث
        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.trim().isEmpty()) {
                searchPopOver.hide(); // إخفاء النافذة إذا كان المربع فارغاً
            } else {
                // استدعاء دالة البحث في قاعدة البيانات
                ObservableList<String> results = fetchResultsFromDatabase(newValue.trim());

                if (results.isEmpty()) {
                    searchPopOver.hide(); // إخفاء النافذة إذا لم توجد نتائج
                } else {
                    searchResultsListView.setItems(results); // تحديث القائمة بالنتائج

                    // إظهار النافذة تحت مربع البحث إذا لم تكن ظاهرة بالفعل
                    if (!searchPopOver.isShowing()) {
                        searchPopOver.show(txtSearch);
                    }
                }
            }
        });

        // 4. حدث عند النقر على إحدى النتائج في القائمة
        searchResultsListView.setOnMouseClicked((MouseEvent event) -> {
            // تنفيذ الإجراء عند النقر المزدوج (Double Click)
            if (event.getClickCount() == 2) {
                String selectedItem = searchResultsListView.getSelectionModel().getSelectedItem();
                if (selectedItem != null) {
                    handleSelectedResult(selectedItem);
                }
            }
        });
    }

    private ObservableList<String> fetchResultsFromDatabase(String searchText) {
        ObservableList<String> results = FXCollections.observableArrayList();

        // =========================================================
        // هنا تضع كود قاعدة البيانات الحقيقي (JDBC أو Hibernate الخ)
        // مثال SQL:
        // SELECT customer_name FROM customers WHERE customer_name LIKE '%searchText%'
        // UNION ALL
        // SELECT item_name FROM items WHERE item_name LIKE '%searchText%'
        // =========================================================

        // بيانات وهمية لاختبار الكود قبل ربطه بقاعدة البيانات:
        String[] mockDatabase = {
                "أحمد محمد - عميل",
                "فاتورة مبيعات #1024",
                "لابتوب ديل - منتج",
                "محمود علي - مورد",
                "شاشة سامسونج - منتج",
                "محمد ابراهيم - موظف"
        };

        for (String item : mockDatabase) {
            // تجاهل حالة الأحرف عند البحث
            if (item.toLowerCase().contains(searchText.toLowerCase())) {
                results.add(item);
            }
        }

        return results;
    }

    private void handleSelectedResult(String selectedItem) {
        log.info("تم اختيار: " + selectedItem);
        searchPopOver.hide(); // إخفاء القائمة بعد الاختيار

        // يمكنك هنا تمرير بيانات العنصر المحدد وفتح الشاشة المناسبة
        // مثلاً: إذا كان يحتوي على كلمة "عميل"، قم بفتح شاشة العملاء وتمرير اسمه
        if(selectedItem.contains("عميل")) {
            // openCustomerProfile(selectedItem);
        } else if (selectedItem.contains("فاتورة")) {
            // openInvoice(selectedItem);
        }

        // تنظيف مربع البحث بعد الاختيار (اختياري)
        txtSearch.clear();
    }
}
