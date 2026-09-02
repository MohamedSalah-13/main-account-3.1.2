package com.hamza.account.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards rule ق-ل9 of {@code docs/new-code-rules.md}: a data-entry screen moves
 * the caret to the next field on Enter.
 * <p>
 * The screens here are filled in by people typing at speed with a barcode
 * scanner in one hand, and a scanner ends its read with an Enter. On a screen
 * that declares no order, that Enter does nothing - the operator scans, looks
 * up, and finds the code sitting in the field they had already left. Reaching
 * for the mouse between every two fields is the same cost paid slowly.
 * <p>
 * {@code Utils.whenEnterPressed(Control...)} is the one way to declare it: the
 * order is written once, in the order the form is actually filled, and it is
 * the argument list itself - so a reader sees the path through the screen
 * without tracing handlers.
 * <p>
 * A screen is judged to be data entry when it declares two or more
 * {@code TextField}s, which is read out of its own source as text. That is
 * blunt in both directions - a screen whose fields live in a form object is not
 * seen, and a pair of read-only search boxes counts - so this is a floor under
 * the obvious omission, not a proof that any screen's order is the right one.
 * Only a person can say that Enter should go from the barcode to the name.
 */
class KeyboardNavigationArchitectureTest {

    private static final Path CONTROLLERS = SourceTree.javaPackage("controller");

    private static final Pattern TEXT_FIELDS = Pattern.compile(
            "(?:@FXML\s*)?(?:private|protected|public)\s+(?:final\s+)?TextField\s+([^;]+);");

    private static final String NAVIGATION_CALL = "whenEnterPressed";

    /**
     * Data-entry screens with no Enter order, as found when this rule was
     * written. Only ever remove entries - the point of the list is that the
     * remaining debt is a number that can be trusted.
     * <p>
     * They were <b>not</b> reviewed one by one. Some are ordinary forms that
     * simply never got the call ({@code AddNameController},
     * {@code AddEmployeeController}, {@code AddUserController}); others hold
     * their two fields for filtering rather than for entry, where an Enter
     * order would be meaningless. Deciding which is which is part of the touch
     * that removes the file from this list.
     */
    private static final Set<String> SCREENS_WITHOUT_AN_ENTER_ORDER = Set.of(
            "com/hamza/account/controller/convert_treasury/TreasuryCashController.java",
            "com/hamza/account/controller/convert_treasury/TreasuryController.java",
            "com/hamza/account/controller/convert_treasury/TreasuryTransferController.java",
            "com/hamza/account/controller/dataByName/AddAreaController.java",
            "com/hamza/account/controller/invoice/TotalsController.java",
            "com/hamza/account/controller/items/StockCountController.java",
            "com/hamza/account/controller/items/StocksController.java",
            "com/hamza/account/controller/items/UpdateSomeItems.java",
            "com/hamza/account/controller/name_account/AccountDetailsWithItemsController.java",
            "com/hamza/account/controller/name_account/AddNameController.java",
            "com/hamza/account/controller/others/AddEmployeeController.java",
            "com/hamza/account/controller/others/AddSubGroupController.java",
            "com/hamza/account/controller/setting/SettingTabBarcodeController.java",
            "com/hamza/account/controller/setting/SettingTabLanguageController.java",
            "com/hamza/account/controller/users/AddUserController.java",
            "com/hamza/account/controller/users/UserPermissionController.java");

    @Test
    void everyNewDataEntryScreenDeclaresItsEnterOrder() {
        var offenders = new TreeSet<>(dataEntryScreensWithoutAnEnterOrder());
        offenders.removeAll(SCREENS_WITHOUT_AN_ENTER_ORDER);
        assertTrue(offenders.isEmpty(),
                "A screen with two or more TextFields is filled in by typing, and a barcode "
                        + "scanner ends its read with an Enter (one-touch rule 9). Declare the order once "
                        + "with Utils." + NAVIGATION_CALL + "(field1, field2, ..., saveButton) in the "
                        + "order the form is actually filled. Screens missing it: " + offenders);
    }

    @Test
    void theListOfScreensWithoutAnEnterOrderStaysHonest() {
        var stale = new TreeSet<>(SCREENS_WITHOUT_AN_ENTER_ORDER);
        stale.removeAll(dataEntryScreensWithoutAnEnterOrder());
        assertTrue(stale.isEmpty(),
                "These screens no longer belong in SCREENS_WITHOUT_AN_ENTER_ORDER - they either "
                        + "declare their Enter order now or are no longer data-entry screens. Remove "
                        + "them from the list, so the remaining debt stays a number worth trusting: "
                        + stale);
    }

    private static List<String> dataEntryScreensWithoutAnEnterOrder() {
        return SourceTree.javaFiles(CONTROLLERS).stream()
                .filter(file -> {
                    String source = SourceTree.withoutComments(SourceTree.readJava(file));
                    return textFieldCount(source) >= 2
                            && !callsNavigationHelper(source);
                })
                .toList();
    }

    private static int textFieldCount(String source) {
        int count = 0;
        Matcher matcher = TEXT_FIELDS.matcher(source);
        while (matcher.find()) {
            for (String name : matcher.group(1).split(",")) {
                if (!name.isBlank()) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * The static import of the helper carries its name too, so a plain text
     * search passes on a screen that imports it and never calls it - which is
     * how {@code UnitsController} slipped through the first run of this guard
     * against a deliberately seeded breach.
     */
    private static boolean callsNavigationHelper(String source) {
        return source.lines()
                .filter(line -> !line.strip().startsWith("import "))
                .anyMatch(line -> line.contains(NAVIGATION_CALL + "("));
    }
}
