package com.hamza.account.manual;

import com.hamza.account.features.rbac.RbacService;
import com.hamza.account.controller.main.MainScreenController;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.config.PropertiesName;
import com.hamza.account.features.export.ReportOutputMode;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.shortcuts.SidebarShortcut;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.account.view.ApplicationNavigator;
import com.hamza.account.view.DownLoadApplication;
import com.hamza.account.view.LogApplication;
import com.hamza.account.view.MainScreenApplication;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Takes the manual's screenshots by opening each screen the way a user does.
 * <p>
 * It stands the whole application up through {@link DownLoadApplication#bootstrapForTooling()},
 * signs in, shows the real main window, and then <b>fires the sidebar button</b> for every screen
 * the manual has a page for, reading the button from
 * {@link MainScreenController#sidebarCommands()} - the map the program itself uses. Nothing here
 * lists the screens: the list is {@code docs/manual}, and the mapping is the sidebar's own, so a
 * screen added to the program and to the manual is photographed without this class changing.
 * <p>
 * <b>Point it at a demo database and nothing else.</b> The pictures go into a document handed to
 * customers, and a screenshot of the developer's own books is a customer list published by
 * accident. {@code ACCOUNT_CONFIG_DIR} is what chooses the database, and {@link ManualCapture}
 * refuses to start without it.
 * <p>
 * What it cannot photograph is written down rather than worked around: a screen that opens a modal
 * dialog of its own, a screen behind a product feature the demo profile does not carry, and a
 * screen whose button the signed-in user may not press. Each is reported at the end and each shows
 * in the PDF as the "not captured yet" box, which is the honest answer - a manual that silently
 * leaves a figure out reads as finished.
 */
public final class ManualCaptureApplication extends Application {

    /** Where the pictures go. */
    private static final String IMAGES = "account.manual.images";
    /** Optional subsystem-only source tree, leaving the complete product manual untouched. */
    private static final String MANUAL_FOLDER = "account.manual.folder";
    private static final String ONLY_PAGE = "account.manual.only";
    /** How long a screen is given to load its data before the picture is taken. */
    private static final String SETTLE = "account.manual.settle";

    private static final double DEFAULT_SETTLE_SECONDS = 2.5;
    /** Twice the screen's own pixels, so a figure is still readable printed on A4. */
    private static final double SCALE = 2.0;
    private static final double CAPTURE_WIDTH = 1366;
    private static final double CAPTURE_HEIGHT = 768;

    private final List<String> captured = new ArrayList<>();
    private final Map<String, String> skipped = new LinkedHashMap<>();
    /** Things worth saying that did not cost a picture. */
    private final List<String> warnings = new ArrayList<>();

    private Path images;
    private double settle;
    private Stage stage;
    private boolean widerThisTime;
    private MainScreenController main;
    private ThemeManager.Theme themeBefore;
    private String reportModeBefore;
    private boolean receiptModeBefore;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        this.images = Path.of(System.getProperty(IMAGES, "docs/manual/images"));
        this.settle = Double.parseDouble(System.getProperty(SETTLE, String.valueOf(DEFAULT_SETTLE_SECONDS)));

        // The manual is photographed in the LIGHT theme, always. Two reasons, and the first is
        // the one that decides it: the dark theme is not finished, so a screenshot taken in it
        // would document a half-built appearance as though it were the product. The second is
        // that a manual is read on paper, where a dark screen prints badly.
        //
        // The theme is a java.util.prefs value belonging to this Windows account, not to the demo
        // database, so it is set for the length of the run and put back in report()/fail().
        themeBefore = ThemeManager.getCurrentTheme();
        reportModeBefore = PropertiesName.getReportPdfOutputMode();
        receiptModeBefore = PropertiesName.getPrintPaperReceiptInvoice();
        ThemeManager.setCurrentTheme(ThemeManager.Theme.LIGHT);
        PropertiesName.setReportPdfOutputMode(ReportOutputMode.PREVIEW.name());
        PropertiesName.setPrintPaperReceiptInvoice(false);

        List<ManualPage> pages;
        try {
            Files.createDirectories(images);
            Path manualFolder = System.getProperty(MANUAL_FOLDER) == null
                    ? ManualSource.folder(Path.of(System.getProperty("account.manual.root", ".")))
                    : Path.of(System.getProperty(MANUAL_FOLDER));
            List<ManualPage> loadedPages = ManualSource.loadFrom(manualFolder);
            String onlyPage = System.getProperty(ONLY_PAGE);
            if (onlyPage != null && !onlyPage.isBlank()) {
                loadedPages = loadedPages.stream().filter(page -> page.id().equals(onlyPage)).toList();
            }
            pages = loadedPages;
        } catch (IOException failure) {
            fail("Could not read the manual", failure);
            return;
        }

        Thread worker = new Thread(() -> {
            try {
                DownLoadApplication.bootstrapForManualCapture();
                Platform.runLater(() -> shootLoginThen(pages));
            } catch (Exception failure) {
                Platform.runLater(() -> fail("Could not start the application", failure));
            }
        }, "manual-capture-bootstrap");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Signs in as user 1. That account bypasses every permission check, which is wrong for a test
     * of authorization and right here: the manual describes the whole program, so the harness has
     * to be able to open every screen in it.
     */
    private void signIn() throws Exception {
        DaoFactory daoFactory = DownLoadApplication.getDaoFactory();
        Users administrator = daoFactory.usersDao().getDataById(1);
        if (administrator == null) {
            throw new IllegalStateException("The demo database has no user 1 to sign in as");
        }
        ServiceRegistry.get(RbacService.class).signIn(administrator);
    }

    /**
     * The login screen, photographed before the harness signs past it.
     * <p>
     * It is the one screen no sidebar button reaches - the harness signs in through
     * {@code RbacService} - and it is the first screen every user ever sees. A page asks for it
     * with {@code stage: LOGIN}.
     */
    private void shootLoginThen(List<ManualPage> pages) {
        Optional<String> picture = pages.stream()
                .filter(page -> page.stage().filter("LOGIN"::equals).isPresent())
                .flatMap(page -> page.screenshot().stream())
                .findFirst();
        if (picture.isEmpty()) {
            signInThen(pages);
            return;
        }
        try {
            new LogApplication(DownLoadApplication.getDaoFactory(), user -> { }).show(stage);
        } catch (Exception failure) {
            skipped.put(picture.get(), "the login screen would not open: " + failure);
            signInThen(pages);
            return;
        }
        after(settle, () -> {
            shoot(picture.get(), stage.getScene().getRoot());
            signInThen(pages);
        });
    }

    private void signInThen(List<ManualPage> pages) {
        try {
            signIn();
        } catch (Exception failure) {
            fail("Could not sign in", failure);
            return;
        }
        showMainWindowThen(pages);
    }

    private void showMainWindowThen(List<ManualPage> pages) {
        try {
            ApplicationNavigator navigator = new ApplicationNavigator(stage, DownLoadApplication.getDaoFactory());
            ServiceRegistry.register(ApplicationNavigator.class, navigator);
            main = new MainScreenApplication(DownLoadApplication.getDaoFactory(), navigator).show(stage);
            // The window opens at whatever size it was left at, and that was 723x478 - smaller than
            // any screen a customer has, so every tabbed screen was photographed cut off on its
            // trailing side and the treasuries table fell out of its own picture. 1366x768 is the
            // smallest screen this ships to: a figure taken there shows what that customer sees.
            stage.setMaximized(false);
            stage.setWidth(CAPTURE_WIDTH);
            stage.setHeight(CAPTURE_HEIGHT);
        } catch (Exception failure) {
            fail("Could not open the main window", failure);
            return;
        }
        // The main window is itself a figure, and it is the one already on screen.
        after(settle, () -> {
            shoot(mainScreenId(pages), stage.getScene().getRoot());
            next(new ArrayList<>(screensToShoot(pages)), 0);
        });
    }

    /** The page that carries {@code shortcut: HOME} is the main window's own page. */
    private String mainScreenId(List<ManualPage> pages) {
        return pages.stream()
                .filter(page -> page.shortcut().filter(SidebarShortcut.HOME.name()::equals).isPresent())
                .flatMap(page -> page.screenshot().stream())
                .findFirst().orElse("main-screen");
    }

    /** Every page that names both a shortcut and a picture, in the manual's own order. */
    private List<ManualPage> screensToShoot(List<ManualPage> pages) {
        return pages.stream()
                .filter(page -> page.shortcut().isPresent() && page.screenshot().isPresent())
                .filter(page -> !page.shortcut().get().equals(SidebarShortcut.HOME.name()))
                .toList();
    }

    /**
     * One screen at a time, each step scheduled after the last rather than looped: opening a screen
     * is asynchronous, and a loop would fire all fifty buttons into one frame and photograph the
     * last one fifty times.
     */
    private void next(List<ManualPage> remaining, int index) {
        if (index >= remaining.size()) {
            report();
            return;
        }
        ManualPage page = remaining.get(index);
        String picture = page.screenshot().orElseThrow();
        Button button = buttonFor(page).orElse(null);
        if (button == null) {
            skipped.put(picture, "no sidebar button for " + page.shortcut().orElse("?"));
            next(remaining, index + 1);
            return;
        }
        if (!button.isVisible() || !button.isManaged() || button.isDisabled()) {
            skipped.put(picture, "the button is not available - the product profile or a permission hides it");
            next(remaining, index + 1);
            return;
        }

        List<Window> before = List.copyOf(Window.getWindows());
        int tabsBefore = tabs().map(pane -> pane.getTabs().size()).orElse(0);
        // Fired through runLater, not from here. This method runs inside the animation pulse that
        // ended the previous screen's pause, and a screen opening with showAndWait - the backup
        // screen is one - is refused there with "showAndWait is not allowed during animation or
        // layout processing". The pause below is started straight away and keeps running even if
        // the fired screen parks the caller in a nested event loop, which is what lets a modal
        // screen be photographed and then closed.
        Platform.runLater(() -> {
            try {
                restoreMainWindow();
                button.fire();
            } catch (RuntimeException failure) {
                skipped.put(picture, "the screen refused to open: " + failure);
            }
        });

        after(settle, () -> {
            Runnable capture = () -> finishPage(page, before, tabsBefore, remaining, index, picture);
            if (page.click().isPresent()) {
                press(page, before, 0, capture);
            } else {
                capture.run();
            }
        });
    }

    private void finishPage(ManualPage page, List<Window> before, int tabsBefore,
                           List<ManualPage> remaining, int index, String picture) {
        List<Stage> opened = newStages(before);
        boolean tabOpened = tabs().map(pane -> pane.getTabs().size() > tabsBefore).orElse(false);
        boolean newestRequested = page.click().stream().anyMatch(click ->
                List.of(click.split("\\s*>>\\s*")).contains("CAPTURE_NEWEST_WINDOW"));
        Optional<Stage> screen = (newestRequested
                ? opened.stream().max(Comparator.comparingInt(opened::indexOf))
                : opened.stream().max(Comparator.comparingDouble(ManualCaptureApplication::area)))
                .filter(candidate -> !isAlert(candidate));
        if (screen.isPresent()) {
            shoot(picture, screen.get().getScene().getRoot());
            opened.forEach(Stage::close);
        } else if (!opened.isEmpty()) {
            skipped.put(picture, "only a dialog opened: " + describe(opened));
            opened.forEach(Stage::close);
        } else if (tabs().map(pane -> pane.getTabs().size()).orElse(0) > tabsBefore) {
            shoot(picture, stage.getScene().getRoot());
        } else {
            skipped.put(picture, "pressing the button opened neither a window nor a tab");
        }
        if (tabOpened) {
            closeNewestTab();
        }
        next(remaining, index + 1);
    }

    /**
     * Puts the main window back to the size the figures are taken at.
     * <p>
     * Measured, not guessed: after the invoice screens - windows of their own - had opened and
     * closed, the stage still reported 1366x768 while its scene had shrunk to 723x478, and every
     * tabbed screen after them was photographed at that size - the root's own preferred size in
     * mainScreen-view.fxml. The stage has to be given a size it does not already hold: setting a
     * property to its own value is a no-op, and nudging it and setting it back inside one pulse
     * comes to the same nothing, which the second attempt proved. So consecutive screens differ
     * by two points, which no reader of a figure can see and which makes every resize a real one.
     */
    private void restoreMainWindow() {
        widerThisTime = !widerThisTime;
        stage.setIconified(false);
        stage.setMaximized(false);
        stage.setWidth(CAPTURE_WIDTH + (widerThisTime ? 2 : 0));
        stage.setHeight(CAPTURE_HEIGHT + (widerThisTime ? 2 : 0));
    }

    private Optional<Button> buttonFor(ManualPage page) {
        return page.shortcut()
                .map(name -> {
                    try {
                        return SidebarShortcut.valueOf(name);
                    } catch (IllegalArgumentException unknown) {
                        return null;
                    }
                })
                .map(shortcut -> main.sidebarCommands().get(shortcut));
    }

    private Optional<TabPane> tabs() {
        return Optional.ofNullable(stage.getScene())
                .map(scene -> scene.lookup("#tabPane"))
                .filter(TabPane.class::isInstance)
                .map(TabPane.class::cast);
    }

    private void closeNewestTab() {
        tabs().ifPresent(pane -> {
            if (pane.getTabs().size() > 1) {
                Tab last = pane.getTabs().getLast();
                pane.getTabs().remove(last);
            }
        });
    }

    private List<Stage> newStages(List<Window> before) {
        return Window.getWindows().stream()
                .filter(window -> !before.contains(window))
                .filter(Window::isShowing)
                .filter(Stage.class::isInstance)
                .map(Stage.class::cast)
                .filter(stage -> stage.getScene() != null)
                .toList();
    }

    /**
     * What a dialog actually said. "A dialog was in the way" is a report nobody can act on; the
     * title and the message name the screen's own refusal, which is usually the real finding.
     */
    private static String describe(List<Stage> stages) {
        List<String> said = new ArrayList<>();
        for (Stage stage : stages) {
            if (stage.getScene().getRoot() instanceof DialogPane pane) {
                String text = String.join(" / ", List.of(
                        String.valueOf(stage.getTitle()),
                        String.valueOf(pane.getHeaderText()),
                        String.valueOf(pane.getContentText()))).replace("null", "").trim();
                said.add(text.isBlank() ? "(an empty dialog)" : text);
            } else {
                said.add("(a window, " + (int) stage.getScene().getWidth() + "x"
                        + (int) stage.getScene().getHeight() + ")");
            }
        }
        return String.join(" | ", said);
    }

    private static double area(Stage stage) {
        return stage.getScene().getWidth() * stage.getScene().getHeight();
    }

    /**
     * An alert, as opposed to a screen that happens to be built as a dialog.
     * <p>
     * Two wrong tests were tried before this one, and each cost screens. Being a
     * {@link DialogPane} is not it: "add a user", "delete data" and the price-check screen are
     * dialogs by construction here, and treating every dialog as an alert threw nine screens away.
     * Size is not it either - some of those screens are a small form.
     * <p>
     * What separates them is <b>where the words are</b>. An alert puts its message in
     * {@code contentText}; a screen puts a scene graph in {@code setContent} and leaves
     * {@code contentText} empty. Measured on all five: the backup screen's alert had content text,
     * the four screens had none.
     */
    private boolean isAlert(Stage stage) {
        return stage.getScene().getRoot() instanceof DialogPane pane
                && pane.getContentText() != null && !pane.getContentText().isBlank();
    }

    private void shoot(String picture, Node node) {
        try {
            SnapshotParameters parameters = new SnapshotParameters();
            parameters.setFill(Color.WHITE);
            parameters.setTransform(javafx.scene.transform.Transform.scale(SCALE, SCALE));
            if (node.getScene() == stage.getScene() && main != null
                    && node.getScene().getWidth() < CAPTURE_WIDTH - 100) {
                // Said rather than shot in silence: a figure this narrow is a cut-off screen.
                System.out.println("WARNING " + picture + ": the main window was photographed at "
                        + (int) node.getScene().getWidth() + "x" + (int) node.getScene().getHeight());
            }
            WritableImage image = node.snapshot(parameters, null);
            Path file = images.resolve(picture + ".png");
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file.toFile());
            captured.add(picture);
        } catch (IOException | RuntimeException failure) {
            skipped.put(picture, "the picture could not be written: " + failure);
        }
    }

    private void after(double seconds, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.seconds(seconds));
        pause.setOnFinished(event -> action.run());
        pause.play();
    }

    /** Wait, do something, wait again, then act - what a screen that has to be asked to load needs. */
    private void after(double seconds, Runnable between, Runnable action) {
        after(seconds, () -> {
            between.run();
            after(seconds, action);
        });
    }

    /**
     * Presses the button a page named with {@code click:}, if it is there.
     * <p>
     * Six of the report screens draw an empty table until "بحث وعرض" is pressed - they are a filter
     * bar with a result area, and opening one is not asking it anything. Photographed without this,
     * the manual's reports chapter documented six filter bars.
     */
    private void press(ManualPage page, List<Window> before, int index, Runnable complete) {
        List<String> actions = page.click().stream().flatMap(value -> java.util.Arrays.stream(value.split("\\s*>>\\s*")))
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
        if (index >= actions.size()) {
            after(settle, complete);
            return;
        }
        String caption = actions.get(index);
        if (caption.equals("CAPTURE_NEWEST_WINDOW")) {
            after(settle, () -> press(page, before, index + 1, complete));
            return;
        }
        if (caption.equals("SELECT_FIRST_ROW")) {
            Optional<TableView<?>> table = topmostTable();
            if (table.isEmpty() || table.get().getItems().isEmpty()) {
                warnings.add(page.screenshot().orElse(page.id()) + ": no row to select");
            } else {
                table.get().getSelectionModel().selectFirst();
            }
            after(settle, () -> press(page, before, index + 1, complete));
            return;
        }
        if (caption.startsWith("ROW_ACTION_ID_")) {
            int actionSeparator = caption.indexOf('_', "ROW_ACTION_ID_".length());
            if (actionSeparator < 0) {
                warnings.add(page.screenshot().orElse(page.id()) + ": invalid row action " + caption);
                after(settle, complete);
                return;
            }
            String rowId = caption.substring("ROW_ACTION_ID_".length(), actionSeparator);
            String actionName = caption.substring(actionSeparator + 1);
            Optional<Button> action = rowActionButton(rowId, actionName);
            if (action.isEmpty()) {
                warnings.add(page.screenshot().orElse(page.id())
                        + ": no row action \"" + actionName + "\" for row " + rowId);
                after(settle, complete);
                return;
            }
            if (index + 1 < actions.size()) {
                after(settle, () -> press(page, before, index + 1, complete));
            } else {
                after(settle, complete);
            }
            Platform.runLater(action.get()::fire);
            return;
        }
        if (caption.equals("DOUBLECLICK_FIRST_ROW") || caption.startsWith("DOUBLECLICK_ROW_ID_")
                || caption.startsWith("DOUBLECLICK_INVOICE_")) {
            Optional<TableView<?>> table = topmostTable();
            if (table.isEmpty() || table.get().getItems().isEmpty()) {
                warnings.add(page.screenshot().orElse(page.id()) + ": no invoice row to open");
                after(settle, complete);
                return;
            }
            TableView<?> view = table.get();
            int rowIndex = 0;
            if (caption.startsWith("DOUBLECLICK_INVOICE_") || caption.startsWith("DOUBLECLICK_ROW_ID_")) {
                String marker = caption.startsWith("DOUBLECLICK_INVOICE_")
                        ? "DOUBLECLICK_INVOICE_" : "DOUBLECLICK_ROW_ID_";
                String invoiceId = caption.substring(marker.length());
                rowIndex = java.util.stream.IntStream.range(0, view.getItems().size())
                        .filter(candidateIndex -> invoiceId.equals(rowId(view.getItems().get(candidateIndex))))
                        .findFirst().orElse(-1);
            }
            if (rowIndex < 0) {
                warnings.add(page.screenshot().orElse(page.id()) + ": requested row was not found");
                after(settle, complete);
                return;
            }
            view.getSelectionModel().select(rowIndex);
            view.scrollTo(rowIndex);
            if (index + 1 < actions.size()) {
                after(settle, () -> press(page, before, index + 1, complete));
            } else {
                after(settle, complete);
            }
            javafx.event.Event event = new MouseEvent(MouseEvent.MOUSE_CLICKED,
                    12, 12, 12, 12, MouseButton.PRIMARY, 2,
                    false, false, false, false, false, false, false,
                    false, false, true, null);
            Platform.runLater(() -> javafx.event.Event.fireEvent(view, event));
            return;
        }
        if (caption.equals("PRINT_INVOICE_1011")) {
            Optional<Stage> invoice = Window.getWindows().stream().filter(Window::isShowing)
                    .filter(Stage.class::isInstance).map(Stage.class::cast)
                    .filter(candidate -> candidate.getScene() != null)
                    .filter(candidate -> containsLabel(candidate.getScene().getRoot(), "فاتورة بيع"))
                    .findFirst();
            Optional<Button> print = invoice.flatMap(candidate -> findButton(candidate.getScene().getRoot(), "طباعة"));
            if (print.isPresent()) {
                if (index + 1 < actions.size()) {
                    after(settle, () -> press(page, before, index + 1, complete));
                } else {
                    after(settle, complete);
                }
                Platform.runLater(print.get()::fire);
            } else {
                warnings.add(page.screenshot().orElse(page.id()) + ": invoice print button was not found");
                after(settle, complete);
            }
            return;
        }
        List<Stage> visible = Window.getWindows().stream().filter(Window::isShowing)
                .filter(Stage.class::isInstance).map(Stage.class::cast)
                .filter(candidate -> candidate.getScene() != null).toList();
        Optional<Button> button = visible.stream()
                .sorted(Comparator.comparingInt(visible::indexOf).reversed())
                .map(candidate -> findButton(candidate.getScene().getRoot(), caption))
                .filter(Optional::isPresent).map(Optional::get).findFirst()
                .or(() -> findButton(stage.getScene().getRoot(), caption));
        if (button.isEmpty()) {
            warnings.add(page.screenshot().orElse(page.id())
                    + ": no button named \"" + caption + "\" - photographed without pressing it");
            after(settle, complete);
            return;
        }
        // Schedule the next step before firing: showAndWait() enters a nested event loop, which
        // is exactly where the following click must run for modal forms and print previews.
        if (index + 1 < actions.size()) {
            after(settle, () -> press(page, before, index + 1, complete));
        } else {
            after(settle, complete);
        }
        try {
            Platform.runLater(button.get()::fire);
        } catch (RuntimeException failure) {
            warnings.add(page.screenshot().orElse(page.id())
                    + ": pressing \"" + caption + "\" failed: " + failure);
        }
    }

    /** The first enabled button whose caption contains the text, anywhere under this node. */
    private Optional<Button> findButton(Node root, String caption) {
        if (root instanceof Button button && !button.isDisabled()) {
            String tooltip = button.getTooltip() == null ? "" : button.getTooltip().getText();
            String accessible = button.getAccessibleText() == null ? "" : button.getAccessibleText();
            if ((button.getText() != null && button.getText().contains(caption))
                    || tooltip.contains(caption) || accessible.contains(caption)) {
                return Optional.of(button);
            }
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Optional<Button> found = findButton(child, caption);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<TableView<?>> findTable(Node root) {
        if (root instanceof TableView<?> table) {
            return Optional.of(table);
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Optional<TableView<?>> found = findTable(child);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<TableView<?>> topmostTable() {
        List<Stage> visible = Window.getWindows().stream().filter(Window::isShowing)
                .filter(Stage.class::isInstance).map(Stage.class::cast)
                .filter(candidate -> candidate.getScene() != null).toList();
        return visible.stream().max(Comparator.comparingInt(visible::indexOf))
                .flatMap(candidate -> findTable(candidate.getScene().getRoot()));
    }

    /** Selects an employee and fires the matching icon in that employee's own action cell. */
    private Optional<Button> rowActionButton(String rowId, String actionName) {
        Optional<TableView<?>> maybeTable = topmostTable();
        if (maybeTable.isEmpty()) {
            return Optional.empty();
        }
        TableView<?> table = maybeTable.get();
        int index = java.util.stream.IntStream.range(0, table.getItems().size())
                .filter(candidate -> rowId.equals(rowId(table.getItems().get(candidate))))
                .findFirst().orElse(-1);
        if (index < 0) {
            return Optional.empty();
        }
        table.getSelectionModel().select(index);
        table.scrollTo(index);
        table.applyCss();
        table.layout();
        return table.lookupAll(".table-row-cell").stream()
                .filter(javafx.scene.control.TableRow.class::isInstance)
                .map(javafx.scene.control.TableRow.class::cast)
                .filter(row -> rowId.equals(rowId(row.getItem())))
                .map(row -> findButton(row, actionName))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private String rowId(Object row) {
        try {
            return String.valueOf(row.getClass().getMethod("getId").invoke(row));
        } catch (ReflectiveOperationException noBeanGetter) {
            try {
                // Feature records expose id() while the older invoice beans expose getId().
                return String.valueOf(row.getClass().getMethod("id").invoke(row));
            } catch (ReflectiveOperationException noRecordAccessor) {
                return "";
            }
        }
    }

    private boolean containsLabel(Node root, String text) {
        if (root instanceof javafx.scene.control.Labeled labeled
                && labeled.getText() != null && labeled.getText().contains(text)) {
            return true;
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (containsLabel(child, text)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void report() {
        restoreTheme();
        System.out.println("captured " + captured.size() + " screenshots into " + images.toAbsolutePath());
        captured.forEach(picture -> System.out.println("  + " + picture));
        if (!warnings.isEmpty()) {
            System.out.println("warnings (" + warnings.size() + "):");
            warnings.forEach(warning -> System.out.println("  ! " + warning));
        }
        if (!skipped.isEmpty()) {
            System.out.println("not captured (" + skipped.size() + "):");
            skipped.forEach((picture, why) -> System.out.println("  - " + picture + ": " + why));
        }
        shutdown(skipped.isEmpty() ? 0 : 1);
    }

    /**
     * Leaves the toolkit before leaving the process.
     * <p>
     * Halting from here killed the JVM inside {@code glass.dll}: the last screen photographed was
     * the backup screen, which opens with {@code showAndWait}, so this code was running inside that
     * screen's nested event loop and the native window layer was mid-operation. The pictures were
     * already on disk, but a run that ends in a crash dump is a run nobody trusts. So: one more
     * pulse for the nested loop to unwind, {@code Platform.exit()}, and {@code halt} only as the
     * last word - the connection pool and the notification scheduler hold non-daemon threads that
     * would otherwise keep the process alive for ever.
     */
    private void shutdown(int code) {
        after(0.5, () -> {
            Platform.exit();
            Thread closer = new Thread(() -> {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                Runtime.getRuntime().halt(code);
            }, "manual-capture-exit");
            closer.setDaemon(true);
            closer.start();
        });
    }

    private void fail(String what, Throwable failure) {
        restoreTheme();
        System.err.println(what);
        failure.printStackTrace();
        shutdown(2);
    }

    /**
     * Puts back the theme this machine was using. A {@code java.util.prefs} value is the one thing
     * a demo database does not isolate - it belongs to the Windows account, not to the schema - so
     * a run that ended without this would leave the developer's own program in the light theme.
     */
    private void restoreTheme() {
        if (themeBefore != null) {
            ThemeManager.setCurrentTheme(themeBefore);
            themeBefore = null;
        }
        if (reportModeBefore != null) {
            PropertiesName.setReportPdfOutputMode(reportModeBefore);
            reportModeBefore = null;
        }
        PropertiesName.setPrintPaperReceiptInvoice(receiptModeBefore);
    }
}
