package com.hamza.account.features.productprofile;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductProfileWiringArchitectureTest {

    @Test
    void optionalScreensAreHiddenAndTheirShortcutsAreNotRegistered() throws Exception {
        String source = read("controller/main/MainScreenController.java");

        assertTrue(source.contains("showFeature(btnMergeItems, ProductFeatures.ITEMS_MERGE)"));
        assertTrue(source.contains("showFeature(btnPriceCheck, ProductFeatures.ITEMS_PRICE_CHECK)"));
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
        assertTrue(fxml.contains("fx:id=\"featureList\""));
    }

    @Test
    void windowsPackageShipsTheIndependentProductSetupLauncher() throws Exception {
        String packageScript = Files.readString(Path.of("../packaging/build-installer.ps1"));
        String launcher = Files.readString(Path.of("../packaging/product-profile-setup.properties"));

        assertTrue(packageScript.contains("AccountK-Product-Setup="));
        assertTrue(launcher.contains("main-class=com.hamza.account.ProductProfileSetupMain"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hamza/account").resolve(relative));
    }
}
