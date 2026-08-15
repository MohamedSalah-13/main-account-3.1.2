package com.hamza.account.controller.main;

import com.hamza.account.config.Image_Setting;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.text.Text;
import javafx.util.Duration;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import org.controlsfx.control.PopOver;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseEvent;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hamza.controlsfx.language.Setting_Language.*;

@Log4j2
public class MainRightPaneController implements Initializable {

    @FXML
    private TextField txtSearch;
    @Getter
    @FXML
    private Button btnSales, btnSalesReturn, btnTotalSale, btnPurchase, btnTotalPurchase, btnPurchaseRe, btnTotalPurchaseRe, btnItems,
            btnAddItem, btnUnits, btnMainGroup, btnSubGroup, btnArea, btnInventory, btnStockCount,
            btnAddCustomerName, btnCustomer, btnAccountCustom, btnAddSupplierName, btnSuppliers,
            btnAccountSuppliers, btnAddEmployee, btnEmployees, btnAddUser, btnUsers,
            btnTreasuryDetails, btnProcess, btnExpenses,
            btnReportSummary, btnReportItems, btnReportItemsDaily, btnReportSalesByYear, btnReportPurchaseByYear,
            btnReportCustomPaid, btnReportSuppliersPaid, btnReportDetails, btnReportYearly, btnReportProfitLoss,
            btnHome, btnSetting, btnShiftReports, btnBackup, btnDeleteData, btnAbout, btnClose;
    @Getter
    @FXML
    private TitledPane paneEmployees, paneSetting;
    @FXML
    private TitledPane paneSales, panePurchase, paneItems, paneCustom, paneSuppliers, paneTreasury, paneReports;
    @FXML
    private Text txtNameProject, txtName, txtTel;
    private PopOver searchPopOver;
    private ListView<GlobalSearchService.Hit> searchResultsListView;
    private final GlobalSearchService globalSearchService = new GlobalSearchService();
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(300));
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sidebar-search");
        thread.setDaemon(true);
        return thread;
    });
    // Bumped on every keystroke so a slow query that finally returns after the
    // user has kept typing (or cleared the box) does not overwrite newer results.
    private long searchRequestId;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        otherSetting();
        setupGlobalSearch();
    }


    private void otherSetting() {
        var imageSetting = new Image_Setting();
        txtSearch.setPromptText(Setting_Language.WORD_SEARCH);
        titlePaneSetting(paneSales, Setting_Language.WORD_SALES, imageSetting.shoppingSales);
        titlePaneSetting(panePurchase, Setting_Language.WORD_PUR, imageSetting.shoppingPurchase);
        titlePaneSetting(paneItems, Setting_Language.WORD_ITEMS, imageSetting.itemWhite);
        titlePaneSetting(paneCustom, Setting_Language.WORD_CUSTOM, imageSetting.personCustomer);
        titlePaneSetting(paneSuppliers, Setting_Language.WORD_SUP, imageSetting.personSup);
        titlePaneSetting(paneEmployees, Setting_Language.EMPLOYEES, imageSetting.account);
        titlePaneSetting(paneTreasury, Setting_Language.TREASURY, imageSetting.treasuryWhite);
        titlePaneSetting(paneReports, Setting_Language.WORD_REPORT, imageSetting.reports);
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
        filterSpecificPane(paneEmployees, lowerCaseSearchText);
        filterSpecificPane(paneTreasury, lowerCaseSearchText);
        filterSpecificPane(paneReports, lowerCaseSearchText);
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
        TitledPane[] allPanes = {paneSales, panePurchase, paneItems, paneCustom, paneSuppliers, paneEmployees, paneTreasury, paneReports, paneSetting};

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
        searchResultsListView = new ListView<>();
        searchResultsListView.setPrefSize(260, 220);
        searchResultsListView.setCellFactory(list -> new SearchHitCell());

        searchPopOver = new PopOver(searchResultsListView);
        searchPopOver.setArrowLocation(PopOver.ArrowLocation.TOP_CENTER);
        searchPopOver.setDetachable(false);
        searchPopOver.setHeaderAlwaysVisible(false);

        searchDebounce.setOnFinished(e -> runSearch(txtSearch.getText()));
        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            searchRequestId++;
            if (newValue == null || newValue.trim().isEmpty()) {
                searchDebounce.stop();
                searchPopOver.hide();
            } else {
                searchDebounce.playFromStart();
            }
        });

        searchResultsListView.setOnMouseClicked((MouseEvent event) -> {
            if (event.getClickCount() == 2) {
                GlobalSearchService.Hit selected = searchResultsListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    handleSelectedResult(selected);
                }
            }
        });

        // The pane lives for the whole main-screen session; only logout tears it
        // down, so the search thread has to be told explicitly rather than relying
        // on the pane being garbage collected.
        txtSearch.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null && newScene == null) {
                searchExecutor.shutdownNow();
            }
        });
    }

    private void runSearch(String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) {
            searchPopOver.hide();
            return;
        }
        long requestId = searchRequestId;
        searchExecutor.submit(() -> {
            try {
                List<GlobalSearchService.Hit> hits = globalSearchService.search(text);
                Platform.runLater(() -> applySearchResults(requestId, hits));
            } catch (Exception e) {
                log.error("Global search failed for '{}'", text, e);
                Platform.runLater(() -> {
                    if (requestId == searchRequestId) searchPopOver.hide();
                });
            }
        });
    }

    private void applySearchResults(long requestId, List<GlobalSearchService.Hit> hits) {
        if (requestId != searchRequestId) return; // a newer keystroke has already superseded this
        if (hits.isEmpty()) {
            searchPopOver.hide();
            return;
        }
        searchResultsListView.getItems().setAll(hits);
        if (!searchPopOver.isShowing()) {
            searchPopOver.show(txtSearch);
        }
    }

    private void handleSelectedResult(GlobalSearchService.Hit hit) {
        searchPopOver.hide();
        txtSearch.clear();

        // Firing the matching sidebar button reuses exactly the navigation (and the
        // permission check) already wired onto it - the search box only has to find
        // the right screen, not know how to open it or whether the user may.
        switch (hit.kind()) {
            case CUSTOMER -> btnCustomer.fire();
            case SUPPLIER -> btnSuppliers.fire();
            case ITEM -> btnItems.fire();
        }
    }

    private static final class SearchHitCell extends ListCell<GlobalSearchService.Hit> {
        @Override
        protected void updateItem(GlobalSearchService.Hit hit, boolean empty) {
            super.updateItem(hit, empty);
            if (empty || hit == null) {
                setText(null);
                return;
            }
            String kindLabel = switch (hit.kind()) {
                case CUSTOMER -> "عميل";
                case SUPPLIER -> "مورد";
                case ITEM -> "صنف";
            };
            String subLabel = hit.subLabel() == null || hit.subLabel().isBlank() ? "" : " - " + hit.subLabel();
            setText(hit.label() + subLabel + " (" + kindLabel + ")");
        }
    }
}
