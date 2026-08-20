package com.hamza.account.architecture;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards rule ق-ل2 of {@code docs/new-code-rules.md}: a model carries no JavaFX.
 * <p>
 * This is a <b>ratchet</b>, not a pass/fail count. The 32 models below are the
 * debt that existed when the rule was written; the list may only shrink. A new
 * model carrying {@code javafx.beans.property} fails
 * {@link #noNewModelCarriesJavaFx()}, and a model that has been cleaned fails
 * {@link #theDebtListStaysHonest()} until it is struck off the list — which is
 * what stops the list from quietly becoming fiction.
 * <p>
 * Why it matters beyond tidiness: a {@code Property} does not serialize to JSON
 * and cannot live on a server, and {@code DForColumnTable} initialises a field
 * with {@code CurrentUser.getOrNull()}, so every row it creates captures whoever
 * happens to be signed in. On a multi-request server that is a silent error.
 */
class ModelPurityArchitectureTest {

    /**
     * Ordered by package then name. <b>Only ever remove entries.</b> Removing one
     * means the model is now a plain POJO; see {@code InvoiceEditorViewModel} for
     * where the {@code Property} belongs instead.
     */
    private static final Set<String> MODELS_STILL_CARRYING_JAVAFX = Set.of(
            "com/hamza/account/model/base/BaseAccount.java",
            "com/hamza/account/model/base/BaseEntity.java",
            "com/hamza/account/model/base/BaseGroups.java",
            "com/hamza/account/model/base/BaseNames.java",
            "com/hamza/account/model/base/BasePurchasesAndSales.java",
            "com/hamza/account/model/base/BaseTotals.java",
            "com/hamza/account/model/base/DForColumnTable.java",
            "com/hamza/account/model/domain/CustomerAccount.java",
            "com/hamza/account/model/domain/Employees.java",
            "com/hamza/account/model/domain/Expenses.java",
            "com/hamza/account/model/domain/ItemsModel.java",
            "com/hamza/account/model/domain/ItemsUnitsModel.java",
            "com/hamza/account/model/domain/Sales.java",
            "com/hamza/account/model/domain/SelPriceTypeModel.java",
            "com/hamza/account/model/domain/Stock.java",
            "com/hamza/account/model/domain/Total_Sales.java",
            "com/hamza/account/model/domain/Total_buy.java",
            "com/hamza/account/model/domain/TreasuryBalance.java",
            "com/hamza/account/model/domain/TreasuryData.java",
            "com/hamza/account/model/domain/TreasuryMovementData.java",
            "com/hamza/account/model/domain/UnitsModel.java",
            "com/hamza/account/model/domain/UserShift.java",
            "com/hamza/account/model/domain/Users.java");

    private static Set<String> modelsImportingJavaFx() {
        var offenders = new TreeSet<String>();
        for (String file : SourceTree.javaFiles(SourceTree.javaPackage("model"))) {
            if (SourceTree.readJava(file).contains("import javafx")) {
                offenders.add(file);
            }
        }
        return offenders;
    }

    @Test
    void noNewModelCarriesJavaFx() {
        var unexpected = new TreeSet<>(modelsImportingJavaFx());
        unexpected.removeAll(MODELS_STILL_CARRYING_JAVAFX);
        assertTrue(unexpected.isEmpty(),
                "A model must be a plain POJO (docs/new-code-rules.md, section 5 rule 2). If a screen needs binding, wrap the model "
                        + "in a view model in the UI layer - see features/invoice/InvoiceEditorViewModel. "
                        + "New models carrying JavaFX: " + unexpected);
    }

    @Test
    void theDebtListStaysHonest() {
        var cleaned = new TreeSet<>(MODELS_STILL_CARRYING_JAVAFX);
        cleaned.removeAll(modelsImportingJavaFx());
        assertTrue(cleaned.isEmpty(),
                "These models no longer carry JavaFX - strike them off MODELS_STILL_CARRYING_JAVAFX "
                        + "so the remaining debt stays accurate: " + cleaned);
    }

    @Test
    void noModelInitialisesAFieldFromGlobalState() {
        var offenders = new TreeSet<String>();
        for (String file : SourceTree.javaFiles(SourceTree.javaPackage("model"))) {
            String source = SourceTree.withoutComments(SourceTree.readJava(file));
            // A field initialised from the signed-in user captures whoever is active
            // at row-construction time. DForColumnTable is the known instance.
            if (source.matches("(?s).*=\\s*CurrentUser\\.get(OrNull)?\\(\\).*")) {
                offenders.add(file);
            }
        }
        offenders.remove("com/hamza/account/model/base/DForColumnTable.java");
        assertTrue(offenders.isEmpty(),
                "A model field must not be initialised from CurrentUser (docs/new-code-rules.md, section 2 rule 1): " + offenders);
    }
}
