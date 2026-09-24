package com.hamza.account.table;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.ThemeManager;
import com.hamza.account.features.export.DirectPdfPrintService;
import com.hamza.account.features.export.PdfPreviewDocument;
import com.hamza.account.features.export.PreviewDocument;
import com.hamza.account.features.export.PreviewPager;
import com.hamza.account.features.totals.PageJump;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.Utils;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import lombok.extern.log4j.Log4j2;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static com.hamza.account.config.PropertiesName.getSettingPrinterNormal;

/**
 * A written report shown page by page before it goes anywhere - printed from here, to a printer and a
 * number of copies chosen here, or saved as a PDF.
 * <p>
 * <b>It shows what will be printed, not a second drawing of the report.</b> A report's PDF is written
 * once by {@link TablePdfReport#write}, exactly as a saved or directly printed one is, and each page is
 * drawn from it by PDFBox - the library the direct print sends it through. A shift's X or Z report is
 * the filled Jasper paper, drawn and printed through Java2D as the thermal printer is sent it. Either
 * way the window asks a {@link PreviewDocument} and knows neither; a paper that cannot be saved as a
 * PDF has no save button.
 * <p>
 * A report's file is a temporary one and is this window's: it is deleted when the window closes, after
 * the document holding it open is closed (Windows will not delete an open file). One page is drawn at a
 * time, on a worker, at the size it is shown; a page asked for and then replaced by another before it
 * was drawn is thrown away rather than shown late.
 * <p>
 * The decisions - which page, what size - are {@link PreviewPager}'s, tested without a toolkit; this
 * class is the controls around them.
 */
@Log4j2
public final class ReportPreviewWindow {

    private static final Duration RESIZE_SETTLE = Duration.millis(120);
    /** Space kept round the page inside the scroll pane, so a fitted page does not touch the edges. */
    private static final double PAGE_MARGIN = 28;

    private final String title;
    /** Opens what is shown, on the worker; a PDF is read from its file there. */
    private final Callable<PreviewDocument> opener;
    /** Runs once the window is closed and the document with it - a report's temporary file goes here. */
    private final Runnable discard;
    /** The printer offered first: the normal one for a report, the thermal one for a shift's paper. */
    private final String defaultPrinter;
    private final boolean savable;
    private final LanguageManager language = LanguageManager.getInstance();
    private final boolean rightToLeft = language.getNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT;
    private final Stage stage = new Stage();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "report-preview");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong ticket = new AtomicLong();
    private final PauseTransition settle = new PauseTransition(RESIZE_SETTLE);

    private final ImageView view = new ImageView();
    private final ScrollPane scroll = new ScrollPane();
    private final TextField pageField = new TextField();
    private final Label pageCount = new Label();
    private final Label zoomLabel = new Label();
    private final Label status = new Label();
    private final ComboBox<String> printer = new ComboBox<>();
    private final Spinner<Integer> copies = new Spinner<>();
    private final Button print = new Button();
    private final Button save = new Button();
    private final Button first = new Button();
    private final Button previous = new Button();
    private final Button next = new Button();
    private final Button last = new Button();
    private final Button zoomIn = new Button();
    private final Button zoomOut = new Button();
    private final Button fitPage = new Button();
    private final Button fitWidth = new Button();
    private final Button close = new Button();

    private volatile PreviewDocument pages;
    private PreviewPager pager;
    private float[] pageWidths = new float[0];
    private float[] pageHeights = new float[0];
    /** The scale the page is shown at now, pixels per point - what a zoom steps from. */
    private double shownScale = 1;
    private int drawnPage = -1;
    private float drawnScale = -1;
    private boolean busy;
    private boolean disposed;

    private ReportPreviewWindow(String title, Callable<PreviewDocument> opener, Runnable discard,
                                String defaultPrinter, boolean savable) {
        this.title = title == null ? "" : title;
        this.opener = opener;
        this.discard = discard;
        this.defaultPrinter = defaultPrinter;
        this.savable = savable;
    }

    /**
     * Opens the preview of a written report. The window takes the file over and deletes it when it
     * closes.
     *
     * @param owner the window the report was asked for from, or null
     */
    public static void open(Window owner, String title, File pdf) {
        new ReportPreviewWindow(title, () -> PdfPreviewDocument.open(pdf, TablePdfReport.configuredPaperSize()),
                () -> {
                    if (pdf.exists() && !pdf.delete()) {
                        pdf.deleteOnExit();
                    }
                }, getSettingPrinterNormal(), true).show(owner);
    }

    /**
     * Opens the preview of a document already in hand - a filled Jasper paper. Call it from the JavaFX
     * thread; the window closes the document when it closes.
     *
     * @param defaultPrinter the printer offered first, as the settings name it
     */
    public static void open(Window owner, String title, PreviewDocument document, String defaultPrinter) {
        new ReportPreviewWindow(title, () -> document, () -> { }, defaultPrinter, document.canSave()).show(owner);
    }

    private void show(Window owner) {
        BorderPane root = new BorderPane();
        root.getStyleClass().addAll("app-root", "report-preview");
        root.setTop(toolbar());
        root.setCenter(canvas());
        root.setBottom(pageBar());

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(root, Math.min(1180, screen.getWidth() * 0.9), screen.getHeight() * 0.92);
        ThemeManager.apply(scene);
        keys(scene);

        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle(language.getString("report.preview.window.title", title));
        stage.setScene(scene);
        stage.setMinWidth(760);
        stage.setMinHeight(480);
        stage.setOnHidden(event -> dispose());
        settle.setOnFinished(event -> showPage());
        scroll.viewportBoundsProperty().addListener((observable, before, after) -> settle.playFromStart());
        setControlsEnabled(false);
        stage.show();
        load();
        loadPrinters();
    }

    // ----------------------------------------------------------------------------- layout

    private Node toolbar() {
        button(print, "report.preview.print", AppIcon.PRINT, "report.preview.print.tip", this::print);
        print.getStyleClass().add("primary-button");
        printer.getStyleClass().add("modern-input");
        printer.setPromptText(language.getString("report.preview.printers.loading"));
        printer.setPrefWidth(240);
        printer.setMinWidth(160);
        printer.setTooltip(new Tooltip(language.getString("report.preview.printer.tip")));
        copies.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, DirectPdfPrintService.MAX_COPIES, 1));
        copies.setPrefWidth(76);
        copies.getStyleClass().add("modern-input");
        Utils.makeTypable(copies);
        copies.getEditor().setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("\\p{Nd}{0,2}") ? change : null));
        Label copiesLabel = new Label(language.getString("report.preview.copies"));
        copiesLabel.getStyleClass().add("form-label");
        button(save, "report.preview.save", AppIcon.SAVE, "report.preview.save.tip", this::save);
        save.getStyleClass().add("neutral-button");
        save.setVisible(savable);
        save.setManaged(savable);

        // A right-to-left window lays the buttons out mirrored but draws each glyph as it is, so the arrow is
        // picked by the direction: the next page is the arrow pointing left in Arabic, right in English.
        iconButton(first, rightToLeft ? AppIcon.CHEVRONS_RIGHT : AppIcon.CHEVRONS_LEFT, "report.preview.first",
                () -> move(() -> pager.first()));
        iconButton(previous, rightToLeft ? AppIcon.CHEVRON_RIGHT : AppIcon.CHEVRON_LEFT, "report.preview.previous",
                () -> move(() -> pager.previous()));
        iconButton(next, rightToLeft ? AppIcon.CHEVRON_LEFT : AppIcon.CHEVRON_RIGHT, "report.preview.next",
                () -> move(() -> pager.next()));
        iconButton(last, rightToLeft ? AppIcon.CHEVRONS_LEFT : AppIcon.CHEVRONS_RIGHT, "report.preview.last",
                () -> move(() -> pager.last()));
        pageField.getStyleClass().add("modern-input");
        pageField.setPrefColumnCount(3);
        pageField.setMinWidth(Region.USE_PREF_SIZE);
        pageField.setAlignment(Pos.CENTER);
        pageField.setTextFormatter(new TextFormatter<>(change ->
                PageJump.isTypablePageText(change.getControlNewText()) ? change : null));
        pageField.setOnAction(event -> goToTypedPage());
        pageField.focusedProperty().addListener((observable, was, is) -> {
            if (!is) {
                goToTypedPage();
            }
        });
        pageCount.getStyleClass().add("form-label");

        iconButton(zoomOut, AppIcon.ZOOM_OUT, "report.preview.zoom.out", this::zoomOut);
        iconButton(zoomIn, AppIcon.ZOOM_IN, "report.preview.zoom.in", this::zoomIn);
        zoomLabel.getStyleClass().add("form-label");
        zoomLabel.setMinWidth(46);
        zoomLabel.setAlignment(Pos.CENTER);
        button(fitPage, "report.preview.fit.page", null, null, () -> fit(true));
        button(fitWidth, "report.preview.fit.width", null, null, () -> fit(false));
        fitPage.getStyleClass().add("neutral-button");
        fitWidth.getStyleClass().add("neutral-button");
        iconButton(close, AppIcon.CLOSE, "report.preview.close", stage::close);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        // What to do with the report above it; where to look in it, below - a toolbar holding both was
        // too wide for 1366 and truncated every caption on it.
        HBox bar = new HBox(8, print, printer, copiesLabel, copies, separator(), save, spacer, close);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 12, 8, 12));
        bar.getStyleClass().add("report-preview-toolbar");
        keepWhole(print, copiesLabel, save, close);
        return bar;
    }

    /** The page's number and the zoom, under the page, with the status line beside them. */
    private Node pageBar() {
        status.getStyleClass().add("info-text");
        status.setText(language.getString("report.preview.loading"));
        status.setMinWidth(0);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8,
                status, spacer,
                first, previous, pageField, pageCount, next, last,
                separator(),
                zoomOut, zoomLabel, zoomIn, fitPage, fitWidth);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 12, 6, 12));
        bar.getStyleClass().add("report-preview-pagebar");
        keepWhole(first, previous, pageCount, next, last, zoomOut, zoomLabel, zoomIn, fitPage, fitWidth);
        return bar;
    }

    /** A control never narrower than its own caption: squeezed, a button reads "ط..." instead of "طباعة". */
    private static void keepWhole(Region... controls) {
        for (Region control : controls) {
            control.setMinWidth(Region.USE_PREF_SIZE);
        }
    }

    private Node canvas() {
        view.setPreserveRatio(true);
        view.setSmooth(true);
        StackPane paper = new StackPane(view);
        paper.setEffect(new DropShadow(14, Color.rgb(0, 0, 0, 0.25)));
        paper.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane desk = new StackPane(paper);
        desk.setPadding(new Insets(PAGE_MARGIN / 2));
        desk.getStyleClass().add("report-preview-canvas");
        // The desk fills the window, so a small page sits in its middle; a page larger than the window
        // makes it larger and the window scrolls.
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setContent(desk);
        scroll.setPannable(true);
        scroll.getStyleClass().add("report-preview-scroll");
        scroll.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isShortcutDown() && pager != null) {
                if (event.getDeltaY() > 0) {
                    zoomIn();
                } else if (event.getDeltaY() < 0) {
                    zoomOut();
                }
                event.consume();
            }
        });
        return scroll;
    }

    private void keys(Scene scene) {
        var accelerators = scene.getAccelerators();
        accelerators.put(new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN), this::print);
        accelerators.put(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), this::save);
        accelerators.put(new KeyCodeCombination(KeyCode.ESCAPE), stage::close);
        accelerators.put(new KeyCodeCombination(KeyCode.PAGE_DOWN), () -> move(() -> pager.next()));
        accelerators.put(new KeyCodeCombination(KeyCode.PAGE_UP), () -> move(() -> pager.previous()));
        accelerators.put(new KeyCodeCombination(KeyCode.HOME, KeyCombination.SHORTCUT_DOWN), () -> move(() -> pager.first()));
        accelerators.put(new KeyCodeCombination(KeyCode.END, KeyCombination.SHORTCUT_DOWN), () -> move(() -> pager.last()));
        accelerators.put(new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.SHORTCUT_DOWN), this::zoomIn);
        accelerators.put(new KeyCodeCombination(KeyCode.ADD, KeyCombination.SHORTCUT_DOWN), this::zoomIn);
        accelerators.put(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.SHORTCUT_DOWN), this::zoomOut);
        accelerators.put(new KeyCodeCombination(KeyCode.SUBTRACT, KeyCombination.SHORTCUT_DOWN), this::zoomOut);
        accelerators.put(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.SHORTCUT_DOWN), () -> fit(true));
    }

    private void button(Button button, String textKey, AppIcon icon, String tipKey, Runnable action) {
        button.setText(language.getString(textKey));
        if (icon != null) {
            button.setGraphic(icon.graphic());
        }
        if (tipKey != null) {
            button.setTooltip(new Tooltip(language.getString(tipKey)));
        }
        button.setOnAction(event -> action.run());
    }

    private void iconButton(Button button, AppIcon icon, String tipKey, Runnable action) {
        button.setGraphic(icon.graphic());
        button.setTooltip(new Tooltip(language.getString(tipKey)));
        button.getStyleClass().add("neutral-button");
        button.setOnAction(event -> action.run());
    }

    /** A gap between two groups of buttons. A drawn separator came out as a bright bar on the dark theme. */
    private static Region separator() {
        Region gap = new Region();
        gap.setMinWidth(14);
        gap.setPrefWidth(14);
        return gap;
    }

    // ----------------------------------------------------------------------------- the document

    /** Opens the document and reads every page's size on the worker; the first page follows. */
    private void load() {
        Task<PreviewDocument> open = new Task<>() {
            @Override
            protected PreviewDocument call() throws Exception {
                PreviewDocument opened = opener.call();
                int count = opened.pageCount();
                float[] widths = new float[count];
                float[] heights = new float[count];
                for (int i = 0; i < count; i++) {
                    widths[i] = opened.pageWidth(i);
                    heights[i] = opened.pageHeight(i);
                }
                pageWidths = widths;
                pageHeights = heights;
                return opened;
            }
        };
        open.setOnSucceeded(event -> {
            if (disposed) {
                // Closed while the file was still opening: nothing will show it, so it goes now.
                closeAndDelete(open.getValue());
                return;
            }
            pages = open.getValue();
            pager = new PreviewPager(Math.max(1, pageWidths.length));
            if (pageWidths.length > 0) {
                pager.open(pageWidths[0], pageHeights[0],
                        scroll.getViewportBounds().getWidth() - PAGE_MARGIN,
                        scroll.getViewportBounds().getHeight() - PAGE_MARGIN);
            }
            setControlsEnabled(true);
            showPage();
        });
        open.setOnFailed(event -> {
            log.error("The report preview could not open its file", open.getException());
            status.setText(language.getString("report.preview.failed"));
        });
        worker.submit(open);
    }

    /** The printers, asked of the system off the JavaFX thread - a network printer can take seconds to answer. */
    private void loadPrinters() {
        Task<List<String>> lookup = new Task<>() {
            @Override
            protected List<String> call() {
                return DirectPdfPrintService.printerNames();
            }
        };
        lookup.setOnSucceeded(event -> {
            List<String> names = lookup.getValue();
            printer.getItems().setAll(names);
            String configured = defaultPrinter;
            if (configured != null && !configured.isBlank()) {
                if (!names.contains(configured)) {
                    // Shown even when it is gone, so the choice the settings made is visible - and fails
                    // with its own message rather than silently becoming another printer.
                    printer.getItems().addFirst(configured);
                }
                printer.setValue(configured);
            } else if (!names.isEmpty()) {
                printer.setValue(names.getFirst());
            }
            printer.setPromptText(language.getString("report.preview.printer.none"));
        });
        lookup.setOnFailed(event -> {
            log.warn("The printers could not be listed", lookup.getException());
            printer.setPromptText(language.getString("report.preview.printer.none"));
        });
        TablePdfReport.start(lookup, "report-preview-printers");
    }

    // ----------------------------------------------------------------------------- showing a page

    private void move(java.util.function.BooleanSupplier step) {
        if (pager != null && step.getAsBoolean()) {
            showPage();
        }
    }

    private void goToTypedPage() {
        if (pager == null) {
            return;
        }
        PageJump.targetPage(pageField.getText(), pager.pageCount()).ifPresentOrElse(
                index -> move(() -> pager.goTo(index)),
                this::updateControls);
        updateControls();
    }

    private void zoomIn() {
        if (pager != null && pager.canZoomIn(shownScale)) {
            pager.zoomIn(shownScale);
            showPage();
        }
    }

    private void zoomOut() {
        if (pager != null && pager.canZoomOut(shownScale)) {
            pager.zoomOut(shownScale);
            showPage();
        }
    }

    private void fit(boolean wholePage) {
        if (pager == null) {
            return;
        }
        if (wholePage) {
            pager.fitPage();
        } else {
            pager.fitWidth();
        }
        showPage();
    }

    /**
     * Sizes the page for the window as it is and draws it if it is not drawn at that size already. The
     * page is drawn at the screen's own pixel density, so it is sharp on a scaled display.
     */
    private void showPage() {
        if (pager == null || pages == null) {
            return;
        }
        int index = pager.page();
        double width = pageWidths[index];
        double height = pageHeights[index];
        double scale = pager.scale(width, height,
                scroll.getViewportBounds().getWidth() - PAGE_MARGIN,
                scroll.getViewportBounds().getHeight() - PAGE_MARGIN);
        shownScale = scale;
        view.setFitWidth(width * scale);
        updateControls();

        double density = stage.getOutputScaleX() > 0 ? stage.getOutputScaleX() : 1;
        float drawScale = (float) Math.min(PreviewDocument.MAX_SCALE, scale * density);
        if (index == drawnPage && Math.abs(drawScale - drawnScale) < 0.01f) {
            return;
        }
        long mine = ticket.incrementAndGet();
        PreviewDocument document = pages;
        worker.submit(() -> {
            if (mine != ticket.get()) {
                return;
            }
            try {
                BufferedImage drawn = document.render(index, drawScale);
                Image image = SwingFXUtils.toFXImage(drawn, null);
                Platform.runLater(() -> {
                    if (mine == ticket.get()) {
                        view.setImage(image);
                        drawnPage = index;
                        drawnScale = drawScale;
                        if (!busy) {
                            status.setText("");
                        }
                    }
                });
            } catch (Exception e) {
                log.error("A page of the report preview could not be drawn", e);
                Platform.runLater(() -> status.setText(language.getString("report.preview.failed")));
            }
        });
    }

    private void updateControls() {
        if (pager == null) {
            return;
        }
        pageField.setText(String.valueOf(pager.page() + 1));
        pageCount.setText(language.getString("report.preview.page.of", pager.pageCount()));
        zoomLabel.setText(Math.round(shownScale * 100) + "%");
        first.setDisable(busy || pager.isFirst());
        previous.setDisable(busy || pager.isFirst());
        next.setDisable(busy || pager.isLast());
        last.setDisable(busy || pager.isLast());
        zoomIn.setDisable(busy || !pager.canZoomIn(shownScale));
        zoomOut.setDisable(busy || !pager.canZoomOut(shownScale));
        fitPage.setDisable(busy || pager.fit() == PreviewPager.Fit.PAGE);
        fitWidth.setDisable(busy || pager.fit() == PreviewPager.Fit.WIDTH);
    }

    private void setControlsEnabled(boolean enabled) {
        for (Node control : List.of(print, printer, copies, save, first, previous, next, last, pageField,
                zoomIn, zoomOut, fitPage, fitWidth)) {
            control.setDisable(!enabled);
        }
        if (enabled) {
            updateControls();
        }
    }

    // ----------------------------------------------------------------------------- printing and saving

    private void print() {
        if (busy || pages == null) {
            return;
        }
        String chosen = printer.getValue();
        if (chosen == null || chosen.isBlank()) {
            status.setText(language.getString("report.preview.printer.none"));
            printer.requestFocus();
            return;
        }
        int count = copies.getValue() == null ? 1 : copies.getValue();
        PreviewDocument document = pages;
        setBusy(true, "report.preview.printing");
        Task<Void> send = new Task<>() {
            @Override
            protected Void call() throws Exception {
                document.print(chosen, count);
                return null;
            }
        };
        send.setOnSucceeded(event -> {
            stage.close();
            AllAlerts.alertSaveWithMessage(language.getString("report.pdf.print.sent", chosen));
        });
        send.setOnFailed(event -> setBusy(false, null));
        AllAlerts.handleTaskFailure(language.getString("report.preview.print.failed"), send);
        TablePdfReport.start(send, "report-preview-print");
    }

    private void save() {
        if (busy || pages == null || !savable) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(language.getString("party.dialog.save.report"));
        chooser.setInitialFileName(TablePdfReport.safeFileName(title) + ".pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File target = chooser.showSaveDialog(stage);
        if (target == null) {
            return;
        }
        PreviewDocument document = pages;
        setBusy(true, null);
        Task<Void> copy = new Task<>() {
            @Override
            protected Void call() throws IOException {
                document.saveAs(target.toPath());
                return null;
            }
        };
        copy.setOnSucceeded(event -> {
            setBusy(false, null);
            status.setText(language.getString("report.preview.saved", target.getAbsolutePath()));
        });
        copy.setOnFailed(event -> setBusy(false, null));
        AllAlerts.handleTaskFailure(language.getString("report.preview.save.failed"), copy);
        TablePdfReport.start(copy, "report-preview-save");
    }

    private void setBusy(boolean value, String statusKey) {
        busy = value;
        print.setDisable(value);
        save.setDisable(value);
        if (statusKey != null) {
            status.setText(language.getString(statusKey));
        } else if (!value) {
            status.setText("");
        }
        updateControls();
    }

    // ----------------------------------------------------------------------------- closing

    /**
     * Stops drawing, closes the document and deletes a report's file - on the worker, after any page it is
     * drawing, so the document is not closed under it. Once only.
     */
    private void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        ticket.incrementAndGet();
        settle.stop();
        PreviewDocument document = pages;
        pages = null;
        worker.submit(() -> closeAndDelete(document));
        worker.shutdown();
    }

    private void closeAndDelete(PreviewDocument document) {
        try {
            if (document != null) {
                document.close();
            }
        } catch (IOException | RuntimeException e) {
            log.warn("The report preview's document did not close cleanly", e);
        } finally {
            discard.run();
        }
    }
}
