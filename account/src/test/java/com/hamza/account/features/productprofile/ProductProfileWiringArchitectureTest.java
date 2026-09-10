package com.hamza.account.features.productprofile;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductProfileWiringArchitectureTest {

    @Test
    void everyFunctionalSidebarSectionUsesTheProductCatalogueAndShortcutsFollowVisibility() throws Exception {
        String source = read("controller/main/MainScreenController.java");
        String menuButton = read("controller/main/MenuButtonSetting.java");

        long gatedScreens = source.lines()
                .filter(line -> line.contains("menuButtonSetting.configureButton("))
                .filter(line -> line.contains("ProductFeatures."))
                .count();
        assertEquals(51, gatedScreens);
        assertTrue(source.contains("ProductFeatures.SALES_CREATE"));
        assertTrue(source.contains("ProductFeatures.REPORT_PROFIT_LOSS"));
        assertTrue(source.contains("ProductFeatures.SYSTEM_DELETE_DATA"));
        assertTrue(source.contains("showCategory(paneSales, ProductFeatures.CATEGORY_SALES)"));
        assertTrue(source.contains("showCategory(paneReports, ProductFeatures.CATEGORY_REPORTS)"));
        assertTrue(menuButton.contains("productFeatures.require(feature)"));
        assertTrue(menuButton.contains("button.setManaged(available)"));
        assertTrue(source.contains("button.isVisible() && button.isManaged()"));
    }

    @Test
    void openingAndServicePathsEnforceTheEditionIndependentlyOfPermissions() throws Exception {
        String priceCheck = read("view/PriceCheckApplication.java");
        String itemMerge = read("features/itemmerge/ItemMergeService.java");
        String itemButtons = read("dash/ItemsButtons.java");

        assertTrue(priceCheck.contains("require(ProductFeatures.ITEMS_PRICE_CHECK)"));
        assertTrue(itemButtons.contains("require(ProductFeatures.ITEMS_MERGE)"));
        assertTrue(itemMerge.contains("productFeatures.require(ProductFeatures.ITEMS_MERGE)"));
        assertTrue(itemMerge.contains("AuthorizationGuard.require(AppPermissions.ITEMS_MERGE)"));
    }

    @Test
    void setupScreenRendersTheSharedCatalogInsteadOfHardCodingFeatureCheckboxes() throws Exception {
        String controller = read("controller/productprofile/ProductProfileSetupController.java");
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/hamza/account/view/product-profile-setup.fxml"));

        assertTrue(controller.contains("catalog.definitions()"));
        assertTrue(controller.contains("ProductEditionPresets.standard(catalog)"));
        assertTrue(fxml.contains("fx:id=\"presetBox\""));
        assertTrue(fxml.contains("fx:id=\"featureList\""));
        assertTrue(fxml.contains("fx:id=\"featureSearchField\""));
        assertTrue(fxml.contains("onAction=\"#exportSummary\""));
    }

    @Test
    void aboutScreenReadsTheVerifiedProfileLoadedAtStartup() throws Exception {
        String bootstrap = read("view/DownLoadApplication.java");
        String about = read("view/AboutApplication.java");

        assertTrue(bootstrap.contains("ServiceRegistry.register(ProductProfile.class, profile)"));
        assertTrue(about.contains("ServiceRegistry.get(ProductProfile.class)"));
        assertTrue(about.contains("product.profile.about.issued"));
    }

    @Test
    void windowsPackageShipsTheIndependentProductSetupLauncher() throws Exception {
        String packageScript = Files.readString(Path.of("../packaging/build-installer.ps1"));
        String launcher = Files.readString(Path.of("../packaging/product-profile-setup.properties"));

        assertTrue(packageScript.contains("AccountK-Product-Setup="));
        assertTrue(launcher.contains("main-class=com.hamza.account.ProductProfileSetupMain"));
        assertTrue(launcher.contains("icon=account/src/main/resources/product-profile-setup.ico"));

        byte[] mainIcon = Files.readAllBytes(Path.of("src/main/resources/tools.ico"));
        byte[] setupIcon = Files.readAllBytes(Path.of("src/main/resources/product-profile-setup.ico"));
        assertFalse(Arrays.equals(mainIcon, setupIcon));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hamza/account").resolve(relative));
    }
}
