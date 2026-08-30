package com.hamza.account.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds an FXML file and the controller that names it to each other.
 * <p>
 * {@code FxmlArchitectureTest} covers the resource keys - the reason five screens were
 * dead at once. This covers the other two ways a screen fails only when a user opens it,
 * neither of which the compiler can see, because both sides are strings:
 * <ul>
 *   <li>{@code onAction="#method"} naming a method the controller does not have. The
 *       loader throws {@code LoadException} and <b>the screen does not open at all</b>.</li>
 *   <li>An {@code @FXML} field whose {@code fx:id} is not in the file. The loader leaves
 *       it null and the screen opens; the {@code NullPointerException} arrives later, on
 *       whichever click first touches that control.</li>
 * </ul>
 * <p>
 * Both are exactly what "just open the screen and click around" catches, which is a
 * check nobody runs on the other fifty screens while changing one of them. Reading the
 * two files against each other costs nothing and runs on every build.
 * <p>
 * Both sides are read as text - the FXML and the controller's own source - so nothing
 * here loads a class, starts a JavaFX toolkit or touches a database. That is also its
 * limit: a handler inherited from a superclass, or one reached through an
 * {@code fx:include}, is not seen, so this is a floor under the obvious mistakes rather
 * than a proof that a screen works.
 */
class FxmlWiringArchitectureTest {

    private static final Pattern CONTROLLER = Pattern.compile("fx:controller=\"([^\"]+)\"");
    private static final Pattern ON_ACTION = Pattern.compile("on[A-Z][A-Za-z]*=\"#([^\"]+)\"");
    private static final Pattern FX_ID = Pattern.compile("fx:id=\"([^\"]+)\"");
    private static final Pattern FXML_FIELD = Pattern.compile(
            "@FXML\\s+(?:private|protected|public)?\\s*(?:final\\s+)?[\\w.<>,?\\[\\]\\s]+?\\s+([\\w,\\s]+);");

    /**
     * Field names declared {@code @FXML} in some controller with no matching
     * {@code fx:id}, as found when this rule was written. Only ever remove entries.
     * <p>
     * They were <b>not</b> investigated one by one, and they are not all the same
     * thing. {@code maskerPaneSetting} was checked and is the harmless kind: the
     * controller assigns it itself ({@code new MaskerPaneSetting(stackPane)}) and the
     * annotation simply lies. The dangerous kind reads identically from here - a field
     * the loader leaves null - so the list is debt to work off, not a clean bill.
     * <p>
     * The list is by <b>name</b> rather than by field-and-file, which is blunt: it
     * exempts that name everywhere. Names like {@code root} and {@code box} are common,
     * so a new screen could hide behind one. Narrowing it to pairs is the improvement
     * to make when the list starts shrinking.
     */
    private static final Set<String> FIELDS_WITHOUT_FX_ID = Set.of(
            "borderPane",
            "box",
            "boxCenter",
            "boxSearch",
            "boxTableArrow",
            "btnPrintXReport",
            "checkShowColumnSelectedInItems",
            "checkValidity",
            "gridPane",
            "hbox",
            "maskerPaneSetting",
            "pane",
            "root",
            "textCopyRight",
            "textData",
            "toolBar");

    private record Screen(String fxml, String controller, String source) {
    }

    /** Every FXML that names a controller, paired with that controller's source. */
    private static Set<Screen> screens() {
        var screens = new LinkedHashSet<Screen>();
        for (String file : SourceTree.fxmlFiles(SourceTree.MAIN_RESOURCES)) {
            String fxml = SourceTree.readResource(file);
            Matcher matcher = CONTROLLER.matcher(fxml);
            if (!matcher.find()) {
                continue;
            }
            String controller = matcher.group(1);
            String path = controller.replace('.', '/') + ".java";
            try {
                screens.add(new Screen(file, controller, SourceTree.readJava(path)));
            } catch (RuntimeException e) {
                throw new IllegalStateException(file + " names a controller with no source at " + path, e);
            }
        }
        return screens;
    }

    @Test
    @DisplayName("the pairing was actually read - the rest would pass vacuously otherwise")
    void screensWereFound() {
        var screens = screens();
        assertFalse(screens.isEmpty(), "no FXML declares fx:controller; the reader is broken");
        assertTrue(screens.size() > 30, "only " + screens.size() + " screens paired; expected most of them");
    }

    @Test
    @DisplayName("every onAction names a method the controller really has")
    void everyHandlerExists() {
        var missing = new TreeSet<String>();
        for (Screen screen : screens()) {
            String source = SourceTree.withoutComments(screen.source());
            Matcher matcher = ON_ACTION.matcher(screen.fxml().isEmpty() ? "" : SourceTree.readResource(screen.fxml()));
            while (matcher.find()) {
                String method = matcher.group(1);
                if (!Pattern.compile("\\b" + Pattern.quote(method) + "\\s*\\(").matcher(source).find()) {
                    missing.add(method + "()  (" + screen.fxml() + " -> " + screen.controller() + ")");
                }
            }
        }
        assertTrue(missing.isEmpty(),
                "FXMLLoader throws LoadException on a handler the controller does not declare, so the "
                        + "screen does not open at all: " + missing);
    }

    @Test
    @DisplayName("every @FXML field has an fx:id in the file that declares the controller")
    void everyInjectedFieldIsNamedInTheFile() {
        var missing = new TreeSet<String>();
        for (Screen screen : screens()) {
            String fxml = SourceTree.readResource(screen.fxml());
            var ids = new LinkedHashSet<String>();
            Matcher idMatcher = FX_ID.matcher(fxml);
            while (idMatcher.find()) {
                ids.add(idMatcher.group(1));
            }

            Matcher fields = FXML_FIELD.matcher(SourceTree.withoutComments(screen.source()));
            while (fields.find()) {
                // One declaration may name several: "private TextField a, b, c;"
                for (String field : fields.group(1).split(",")) {
                    String name = field.trim();
                    if (name.isEmpty() || FIELDS_WITHOUT_FX_ID.contains(name) || ids.contains(name)) {
                        continue;
                    }
                    missing.add(name + "  (" + screen.controller() + " -> " + screen.fxml() + ")");
                }
            }
        }
        assertTrue(missing.isEmpty(),
                "An @FXML field with no matching fx:id either is left null by the loader - and the "
                        + "NullPointerException waits for the first click that touches it - or is assigned "
                        + "in code and should not carry the annotation at all. Both are worth fixing "
                        + "before the screen ships: " + missing);
    }
}
