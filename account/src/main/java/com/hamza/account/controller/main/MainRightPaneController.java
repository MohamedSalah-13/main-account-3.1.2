package com.hamza.account.controller.main;

import com.hamza.account.config.Image_Setting;
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

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        otherSetting();
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
            filterTitledPaneButtons(newValue);
        });

        txtNameProject.setText(PROGRAM_TITLE);
        txtName.setText(PROGRAM_NAME_EN);
        txtTel.setText(PROGRAM_TEL);
    }


    private void titlePaneSetting(TitledPane titledPane, String text, InputStream stream) {
        titledPane.setText(text);
        titledPane.setGraphic(new ImageDesign(stream, 20));
    }

    /**
     * Filters the TitledPane buttons based on the search text.
     * Shows only the TitledPanes that contain buttons with text matching the search text.
     * If search text is empty, all TitledPanes are shown.
     *
     * @param searchText The text to filter buttons by
     */
    private void filterTitledPaneButtons(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            // Show all TitledPanes if search text is empty
            setTitledPaneVisibility(paneSales, true);
            setTitledPaneVisibility(panePurchase, true);
            setTitledPaneVisibility(paneItems, true);
            setTitledPaneVisibility(paneCustom, true);
            setTitledPaneVisibility(paneSuppliers, true);
            setTitledPaneVisibility(paneTreasury, true);
            setTitledPaneVisibility(paneSetting, true);
            return;
        }

        // Convert search text to lowercase for case-insensitive comparison
        String lowerCaseSearchText = searchText.toLowerCase();

        // Filter each TitledPane based on its buttons' text
        setTitledPaneVisibility(paneSales, containsButtonWithText(paneSales, lowerCaseSearchText));
        setTitledPaneVisibility(panePurchase, containsButtonWithText(panePurchase, lowerCaseSearchText));
        setTitledPaneVisibility(paneItems, containsButtonWithText(paneItems, lowerCaseSearchText));
        setTitledPaneVisibility(paneCustom, containsButtonWithText(paneCustom, lowerCaseSearchText));
        setTitledPaneVisibility(paneSuppliers, containsButtonWithText(paneSuppliers, lowerCaseSearchText));
        setTitledPaneVisibility(paneTreasury, containsButtonWithText(paneTreasury, lowerCaseSearchText));
        setTitledPaneVisibility(paneSetting, containsButtonWithText(paneSetting, lowerCaseSearchText));
    }

    /**
     * Sets the visibility of a TitledPane.
     * Uses both visible and managed properties to ensure proper layout behavior.
     *
     * @param titledPane The TitledPane to set visibility for
     * @param visible Whether the TitledPane should be visible
     */
    private void setTitledPaneVisibility(TitledPane titledPane, boolean visible) {
        titledPane.setVisible(visible);
        titledPane.setManaged(visible); // This ensures layout space is not reserved for invisible panes
    }

    /**
     * Checks if a TitledPane contains any button with text that matches the search text.
     *
     * @param titledPane The TitledPane to check
     * @param searchText The search text (lowercase)
     * @return true if the TitledPane contains a matching button, false otherwise
     */
    private boolean containsButtonWithText(TitledPane titledPane, String searchText) {
        // Check if the TitledPane's text matches
        if (titledPane.getText() != null && titledPane.getText().toLowerCase().contains(searchText)) {
            return true;
        }

        // If the TitledPane is not expanded, we need to expand it temporarily to access its content
        boolean wasExpanded = titledPane.isExpanded();
        if (!wasExpanded) {
            titledPane.setExpanded(true);
        }

        try {
            // Check if any button in the TitledPane's content matches
            if (titledPane.getContent() != null) {
                return titledPane.getContent().lookupAll(".button").stream()
                        .filter(node -> node instanceof Button)
                        .map(node -> (Button) node)
                        .anyMatch(button -> button.getText() != null &&
                                button.getText().toLowerCase().contains(searchText));
            }
            return false;
        } finally {
            // Restore the original expanded state
            if (!wasExpanded) {
                titledPane.setExpanded(wasExpanded);
            }
        }
    }
}
