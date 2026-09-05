package com.hamza.account.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards rule ق-ل3 of {@code docs/new-code-rules.md}.
 * <p>
 * Two of the three checks here are <b>strict, with no baseline</b>, because they
 * pin behaviour that is correct today and whose breach is not a style problem but
 * a screen that will not open. {@code FXMLLoader} throws {@code LoadException} on
 * the first {@code %key} it meets when the bundle is null, <i>and</i> when the key
 * is absent from the bundle it was given. Five screens were dead for exactly the
 * first reason - {@code MonthlySalesView}, {@code customer-purchased-items-view},
 * {@code CustomerReceivableView}, {@code ItemSalesRankView} and
 * {@code DailyItemSalesView} - and nothing in the build noticed.
 * <p>
 * The third check, {@code fx:controller}, is a ratchet: 42 of the original 46
 * files have been given the declaration. {@code OpenFxmlApplication.bindController}
 * already installs a controller factory, so declaring it does not conflict with
 * constructor injection - the payoff is that the IDE ties an {@code @FXML} field to
 * its file, making a renamed {@code fx:id} an editor error rather than something a
 * user discovers. Four screens that loaded their controller with a raw
 * {@code FXMLLoader.setController(...)} were switched to
 * {@code setControllerFactory(type -> controller)} instead, which is the same
 * technique {@code OpenFxmlApplication} uses and is what let the declaration go in
 * without a "Controller value already specified" exception at load time.
 */
class FxmlArchitectureTest {

    private static final Pattern RESOURCE_KEY = Pattern.compile("=\"%([^\"]+)\"");
    private static final Pattern FXML_IN_STATEMENT = Pattern.compile("\"([^\"]*\\.fxml)\"");

    /**
     * Read from the sibling module rather than the classpath: the tests run on the
     * module path, where {@code controlsfx} does not open its resources to
     * {@code com.hamza.account}, so {@code getResourceAsStream} returns null.
     */
    private static final Path BUNDLE_DIR =
            Path.of("..", "controlsfx", "src", "main", "resources", "i18n");

    private static final String[] BUNDLES = {
            "messages.properties",
            "messages_ar.properties",
            "messages_en.properties"};

    /**
     * FXML files that do not yet declare {@code fx:controller}. Only ever remove
     * entries.
     * <p>
     * These four are not the same kind of debt as the rest: nothing in
     * {@code src/main/java} loads them under any name - no {@code @FxmlPath}, no
     * {@code getResource} call, nothing. There is no controller to name, so
     * {@code fx:controller} cannot be added without inventing one. Leave them here
     * until someone determines whether the screen is dead weight to delete or a
     * feature that was never wired up.
     */
    private static final Set<String> FXML_WITHOUT_CONTROLLER = Set.of(
            "com/hamza/account/view/add-treasury-amount.fxml",
            "com/hamza/account/view/addStock-view.fxml",
            "com/hamza/account/view/excel-view.fxml",
            "com/hamza/account/view/reports/report-print.fxml");

    private static Properties bundle(String name) {
        Path path = BUNDLE_DIR.resolve(name);
        assertTrue(Files.isRegularFile(path),
                "bundle missing - if the bundles moved, update BUNDLE_DIR rather than deleting "
                        + "this check, which would let the guard pass while seeing nothing: " + path);
        Properties properties = new Properties();
        // Properties.load reads ISO-8859-1 and decodes the unicode escapes these
        // files are written in, which is what FXMLLoader will do at run time.
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }

    /** Maps every {@code .fxml} basename to whether it contains at least one {@code %key}. */
    private static Map<String, Boolean> fxmlUsesResourceKeys() {
        var byName = new LinkedHashMap<String, Boolean>();
        for (String file : SourceTree.fxmlFiles(SourceTree.MAIN_RESOURCES)) {
            String name = file.substring(file.lastIndexOf('/') + 1);
            byName.put(name, RESOURCE_KEY.matcher(SourceTree.readResource(file)).find());
        }
        return byName;
    }

    @Test
    void everyResourceKeyResolvesInAllThreeBundles() {
        var missing = new TreeSet<String>();
        for (String resource : BUNDLES) {
            Properties properties = bundle(resource);
            for (String file : SourceTree.fxmlFiles(SourceTree.MAIN_RESOURCES)) {
                Matcher matcher = RESOURCE_KEY.matcher(SourceTree.readResource(file));
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (!properties.containsKey(key)) {
                        missing.add(key + "  (" + file + " -> " + resource + ")");
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(),
                "FXMLLoader throws LoadException when a %key is absent from the bundle it was given, "
                        + "so this breaks the screen in that language only (docs/new-code-rules.md, section 5 rule 3). Missing: " + missing);
    }

    @Test
    void anFxmlUsingResourceKeysIsNeverLoadedWithoutABundle() {
        Map<String, Boolean> usesKeys = fxmlUsesResourceKeys();
        var offenders = new TreeSet<String>();

        for (String file : SourceTree.javaFiles(SourceTree.MAIN_JAVA)) {
            String source = SourceTree.withoutComments(SourceTree.readJava(file));
            int from = 0;
            while (true) {
                int start = source.indexOf("new FXMLLoader", from);
                if (start < 0) {
                    break;
                }
                int end = source.indexOf(';', start);
                from = start + 1;
                if (end < 0) {
                    continue;
                }
                String statement = source.substring(start, end);
                Matcher named = FXML_IN_STATEMENT.matcher(statement);
                if (!named.find()) {
                    // No literal path in the statement: OpenFxmlApplication, which
                    // passes the bundle for every screen that goes through it.
                    continue;
                }
                String path = named.group(1);
                String name = path.substring(path.lastIndexOf('/') + 1);
                if (Boolean.TRUE.equals(usesKeys.get(name)) && !statement.contains("esourceBundle")) {
                    offenders.add(file + " -> " + name);
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "This FXML contains %key and is loaded without a ResourceBundle, so FXMLLoader throws "
                        + "LoadException and the screen never opens (docs/new-code-rules.md, section 5 rule 3). Pass "
                        + "LanguageManager.getInstance().getResourceBundle(), or load through "
                        + "OpenFxmlApplication: " + offenders);
    }

    /**
     * A package holding a controller with {@code @FXML} fields must be opened to
     * {@code javafx.fxml} in {@code module-info.java}.
     * <p>
     * Written after the price-check screen was opened for the first time and died on
     * {@code InaccessibleObjectException: module com.hamza.account does not "opens
     * com.hamza.account.controller.pricecheck" to module javafx.fxml}. The loader
     * reaches an {@code @FXML} field by reflection, and on the module path a package
     * that is not opened refuses it - so the screen throws a {@code LoadException} at
     * the first field and <b>never opens at all</b>.
     * <p>
     * Nothing else in the build sees it: the code compiles, every other guard here
     * passes, and the failure arrives the first time a person clicks the button. Every
     * existing controller package is already opened, which is why this rule carries no
     * baseline - the omission only happens on a <i>new</i> package, which is exactly
     * when nobody thinks of {@code module-info}.
     */
    @Test
    void everyControllerPackageIsOpenedToTheFxmlLoader() {
        String moduleInfo = SourceTree.readJava("module-info.java");
        var offenders = new TreeSet<String>();
        for (String file : SourceTree.javaFiles(SourceTree.javaPackage("controller"))) {
            if (!SourceTree.readJava(file).contains("@FXML")) {
                continue;
            }
            String packageName = file.substring(0, file.lastIndexOf('/')).replace('/', '.');
            if (!moduleInfo.contains("opens " + packageName + " to javafx.fxml")) {
                offenders.add(packageName);
            }
        }
        assertTrue(offenders.isEmpty(),
                "A package whose controller declares @FXML fields must be opened to javafx.fxml in "
                        + "module-info.java, or the loader cannot inject them and the screen dies "
                        + "with an InaccessibleObjectException the first time it is opened. Add "
                        + "\"opens <package> to javafx.fxml;\" for: " + offenders);
    }

    @Test
    void noNewFxmlOmitsItsController() {
        var unexpected = new TreeSet<String>();
        for (String file : SourceTree.fxmlFiles(SourceTree.MAIN_RESOURCES)) {
            if (!SourceTree.readResource(file).contains("fx:controller")
                    && !FXML_WITHOUT_CONTROLLER.contains(file)) {
                unexpected.add(file);
            }
        }
        assertTrue(unexpected.isEmpty(),
                "A new FXML must declare fx:controller (docs/new-code-rules.md, section 5 rule 3); OpenFxmlApplication installs a "
                        + "controller factory, so this does not conflict with constructor injection: "
                        + unexpected);
    }

    @Test
    void theDebtListStaysHonest() {
        var declared = new TreeSet<String>();
        for (String file : FXML_WITHOUT_CONTROLLER) {
            if (SourceTree.readResource(file).contains("fx:controller")) {
                declared.add(file);
            }
        }
        assertTrue(declared.isEmpty(),
                "These FXML files now declare fx:controller - strike them off FXML_WITHOUT_CONTROLLER "
                        + "so the remaining debt stays accurate: " + declared);
    }

    @Test
    void theGuardItselfSeesTheScreensItIsMeantToProtect() {
        // A regex that silently matches nothing would make every check above pass.
        Map<String, Boolean> usesKeys = fxmlUsesResourceKeys();
        assertFalse(usesKeys.isEmpty(), "no FXML found - the walk is wrong");
        assertTrue(Boolean.TRUE.equals(usesKeys.get("MonthlySalesView.fxml")),
                "MonthlySalesView.fxml is known to use %key; the scanner no longer sees it");
    }
}
