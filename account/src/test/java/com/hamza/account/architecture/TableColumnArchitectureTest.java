package com.hamza.account.architecture;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards rule ق-ل1 of {@code docs/new-code-rules.md}: table columns are built in
 * code, never declared with {@code @ColumnData}.
 * <p>
 * {@code TableColumnAnnotation} builds columns through {@code PropertyValueFactory},
 * which resolves a field by <i>string</i> at run time. A renamed field therefore
 * produces a silently empty column - no compile error, no exception. That is what
 * these 170 annotations are: 170 unchecked strings.
 * <p>
 * Both counts here may <b>only go down</b>. The end state is the deletion of
 * {@code ColumnData} and {@code TableColumnAnnotation}, tracked as §12 of
 * {@code docs/erp-roadmap.md}. Note the ordering constraint recorded there:
 * this rule must be finished <i>before</i> the models are cleaned (ق-ل2), because
 * {@code PropertyValueFactory} is precisely what holds the properties in place.
 */
class TableColumnArchitectureTest {

    /** Annotation sites when the rule was written. Only ever lower this number. */
    private static final int ANNOTATION_BASELINE = 170;

    /** Call sites of the reflective builder when the rule was written. Only ever lower this. */
    private static final int BUILDER_CALL_BASELINE = 21;

    private static final Pattern ANNOTATION = Pattern.compile("@ColumnData\\b");
    private static final Pattern BUILDER_CALL = Pattern.compile("new\\s+TableColumnAnnotation\\s*\\(\\s*\\)");

    /** Files carrying at least one {@code @ColumnData}. Only ever remove entries. */
    private static final Set<String> FILES_STILL_USING_THE_ANNOTATION = Set.of(
            "com/hamza/account/controller/model/TableData.java",
            "com/hamza/account/controller/model/TableTotals.java",
            "com/hamza/account/model/base/BaseAccount.java",
            "com/hamza/account/model/base/BaseEntity.java",
            "com/hamza/account/model/base/BaseGroups.java",
            "com/hamza/account/model/base/BaseNames.java",
            "com/hamza/account/model/base/BasePurchasesAndSales.java",
            "com/hamza/account/model/base/BaseTotals.java",
            "com/hamza/account/model/base/UnitExtends.java",
            "com/hamza/account/model/domain/Area.java",
            "com/hamza/account/model/domain/Audit_log.java",
            "com/hamza/account/model/domain/CardItems.java",
            "com/hamza/account/model/domain/CustomerPurchasedItem.java",
            "com/hamza/account/model/domain/CustomerReceivable.java",
            "com/hamza/account/model/domain/Employees.java",
            "com/hamza/account/model/domain/Expenses.java",
            "com/hamza/account/model/domain/ExpensesDetails.java",
            "com/hamza/account/model/domain/ItemsMiniQuantity.java",
            "com/hamza/account/model/domain/ItemsModel.java",
            "com/hamza/account/model/domain/ItemsUnitsModel.java",
            "com/hamza/account/model/domain/SelPriceTypeModel.java",
            "com/hamza/account/model/domain/Stock.java",
            "com/hamza/account/model/domain/TableDataReports.java",
            "com/hamza/account/model/domain/Treasury.java",
            "com/hamza/account/model/domain/TreasuryBalance.java",
            "com/hamza/account/model/domain/TreasuryData.java",
            "com/hamza/account/model/domain/UnitsModel.java",
            "com/hamza/account/model/domain/UserShift.java",
            "com/hamza/account/model/domain/Users.java",
            "com/hamza/account/view/barcode/PrintBarcodeModel.java");

    private static int countAcrossMainJava(Pattern pattern) {
        int total = 0;
        for (String file : SourceTree.javaFiles(SourceTree.MAIN_JAVA)) {
            Matcher matcher = pattern.matcher(SourceTree.withoutComments(SourceTree.readJava(file)));
            while (matcher.find()) {
                total++;
            }
        }
        return total;
    }

    private static Set<String> filesUsingTheAnnotation() {
        var offenders = new TreeSet<String>();
        for (String file : SourceTree.javaFiles(SourceTree.MAIN_JAVA)) {
            if (ANNOTATION.matcher(SourceTree.withoutComments(SourceTree.readJava(file))).find()) {
                offenders.add(file);
            }
        }
        return offenders;
    }

    @Test
    void theAnnotationCountOnlyGoesDown() {
        int actual = countAcrossMainJava(ANNOTATION);
        assertTrue(actual <= ANNOTATION_BASELINE,
                "Declare table columns in the controller, not with @ColumnData (docs/new-code-rules.md, section 5 rule 1). "
                        + "PropertyValueFactory resolves the field by name at run time, so a rename "
                        + "yields an empty column with no compile error. Baseline "
                        + ANNOTATION_BASELINE + ", found " + actual
                        + ". If you removed some, lower ANNOTATION_BASELINE to " + actual + ".");
    }

    @Test
    void theReflectiveBuilderCountOnlyGoesDown() {
        int actual = countAcrossMainJava(BUILDER_CALL);
        assertTrue(actual <= BUILDER_CALL_BASELINE,
                "No new call to TableColumnAnnotation (docs/new-code-rules.md, section 5 rule 1). Baseline "
                        + BUILDER_CALL_BASELINE + ", found " + actual
                        + ". If you removed some, lower BUILDER_CALL_BASELINE to " + actual + ".");
    }

    @Test
    void noNewFileAdoptsTheAnnotation() {
        var unexpected = new TreeSet<>(filesUsingTheAnnotation());
        unexpected.removeAll(FILES_STILL_USING_THE_ANNOTATION);
        assertTrue(unexpected.isEmpty(),
                "New files declaring columns with @ColumnData (docs/new-code-rules.md, section 5 rule 1): " + unexpected);
    }

    @Test
    void theDebtListStaysHonest() {
        var cleaned = new TreeSet<>(FILES_STILL_USING_THE_ANNOTATION);
        cleaned.removeAll(filesUsingTheAnnotation());
        assertTrue(cleaned.isEmpty(),
                "These files no longer use @ColumnData - strike them off "
                        + "FILES_STILL_USING_THE_ANNOTATION so the remaining debt stays accurate: " + cleaned);
    }
}
