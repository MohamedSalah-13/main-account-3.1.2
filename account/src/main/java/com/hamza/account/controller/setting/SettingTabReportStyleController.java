package com.hamza.account.controller.setting;

import com.hamza.account.config.AppIcon;
import com.hamza.account.config.SharedSettingKeys;
import com.hamza.account.features.export.PageNumbering;
import com.hamza.account.features.export.PdfExportService;
import com.hamza.account.features.export.ReportPalette;
import com.hamza.account.features.export.ReportSetup;
import com.hamza.account.features.export.ReportStyle;
import com.hamza.account.features.export.ReportStyleSample;
import com.hamza.account.table.ShopReportSetup;
import com.hamza.account.table.TablePdfReport;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.Utils;
import com.hamza.controlsfx.table.Columns;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import lombok.extern.log4j.Log4j2;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The tab where the shop decides how its printed pages look - {@link ReportStyle}, one choice for every
 * report and every invoice printed as a PDF - with the page itself drawn beside the controls.
 * <p>
 * <b>A change is saved as it is made</b>, like every other setting here, but only once the controls have
 * been still for a moment: a spinner's arrow held down would otherwise write the shop's setting to the
 * database once per step. The same pause redraws the preview.
 * <p>
 * <b>The preview is the real renderer on made-up content</b> ({@link ReportStyleSample}): the file is
 * written by {@link PdfExportService} exactly as a report is, and drawn back by PDFBox, the library the
 * direct print already uses. Nothing here decides how a page looks, so the preview cannot disagree with
 * the paper. It is drawn off the JavaFX thread and an answer to a change that has since been replaced
 * is thrown away.
 */
@Log4j2
public class SettingTabReportStyleController {

    private static final Duration SETTLE = Duration.millis(400);
    /** Pixels per point the preview is drawn at: an A4 page is about 830 pixels wide. */
    private static final float PREVIEW_SCALE = 1.4f;

    private final LanguageManager language = LanguageManager.getInstance();
    private final PauseTransition settle = new PauseTransition(SETTLE);
    private final AtomicLong generation = new AtomicLong();

    private final Spinner<Integer> titleSize = sizeSpinner();
    private final Spinner<Integer> subtitleSize = sizeSpinner();
    private final Spinner<Integer> headerSize = sizeSpinner();
    private final Spinner<Integer> bodySize = sizeSpinner();
    private final Spinner<Integer> totalsSize = sizeSpinner();
    private final Spinner<Integer> smallSize = sizeSpinner();
    private final CheckBox compactRows = check("report.style.compact");

    private final CheckBox showLetterhead = check("report.style.show.letterhead");
    private final CheckBox showTitle = check("report.style.show.title");
    private final CheckBox showSubtitle = check("report.style.show.subtitle");
    private final CheckBox showPrintedAt = check("report.style.show.printed.at");
    private final CheckBox showPrintedBy = check("report.style.show.printed.by");
    private final CheckBox showFooter = check("report.style.show.footer");
    private final TextField footerText = new TextField();
    private final ComboBox<PageNumbering> numbering = new ComboBox<>();

    private final ComboBox<ReportPalette> palette = new ComboBox<>();
    private final CheckBox inkSaver = check("report.style.ink.saver");

    private final CheckBox showDocumentLetterhead = check("report.style.document.letterhead");
    private final Spinner<Integer> documentTopSpace =
            spinner(0, ReportStyle.MAX_TOP_SPACE_MM, 0, 5);

    private final ToggleButton previewReport = new ToggleButton();
    private final ToggleButton previewDocument = new ToggleButton();
    private final ToggleButton zoom = new ToggleButton();
    private final ImageView preview = new ImageView();
    private final Label previewStatus = new Label();

    /** What the shop has stored - a change is written only when the controls say something else. */
    private ReportStyle saved;
    /** The company, the words and the user, read once on the first preview; each change swaps the style. */
    private volatile ReportSetup previewBase;
    /** Set while the controls are being filled, so filling them is not taken for a change. */
    private boolean filling;
    private boolean previewStarted;

    public Parent build() {
        saved = ShopReportSetup.storedStyle();
        configureControls();
        show(saved);

        BorderPane root = new BorderPane();
        // report-style: the theme colours this tab's card titles, which the shared title's blue loses on dark.
        root.getStyleClass().addAll("app-root", "report-style");
        root.setTop(header());
        HBox body = new HBox(12, formPane(), previewPane());
        body.setPadding(new Insets(0, 12, 12, 12));
        root.setCenter(body);

        settle.setOnFinished(event -> apply());
        return root;
    }

    /**
     * Draws the preview the first time the tab is looked at. The settings screen builds every tab when it
     * opens, and most openings are for something else: a company read and a page drawn each time would be
     * spent on a tab nobody chose.
     */
    public void shown() {
        if (!previewStarted) {
            previewStarted = true;
            renderPreview(read());
        }
    }

    // ----------------------------------------------------------------------------- layout

    private Node header() {
        Label title = new Label(text("report.style.page.title"));
        title.getStyleClass().add("settings-title");
        // The style is the shop's: the mark is what tells the person changing it that every till prints so.
        SettingScope.shared(title, SharedSettingKeys.REPORT_PDF_STYLE);
        Label subtitle = new Label(text("report.style.page.subtitle"));
        subtitle.getStyleClass().add("settings-subtitle");
        subtitle.setWrapText(true);
        VBox header = new VBox(4, title, subtitle);
        header.setPadding(new Insets(12));
        return header;
    }

    private Node formPane() {
        Button reset = new Button(text("report.style.reset"), AppIcon.REFRESH.graphic());
        reset.getStyleClass().add("neutral-button");
        reset.setOnAction(event -> {
            show(ReportStyle.DEFAULT);
            changed();
        });
        HBox actions = new HBox(reset);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox form = new VBox(14, fontsCard(), headAndFootCard(), coloursCard(), documentsCard(), actions);
        form.getStyleClass().add("app-container");
        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("settings-scroll-pane");
        scroll.setPrefWidth(540);
        scroll.setMinWidth(420);
        return scroll;
    }

    private Node fontsCard() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.getColumnConstraints().addAll(new ColumnConstraints(), grow(), new ColumnConstraints(), grow());
        addSize(grid, 0, 0, "report.style.size.title", titleSize);
        addSize(grid, 0, 2, "report.style.size.subtitle", subtitleSize);
        addSize(grid, 1, 0, "report.style.size.header", headerSize);
        addSize(grid, 1, 2, "report.style.size.body", bodySize);
        addSize(grid, 2, 0, "report.style.size.totals", totalsSize);
        addSize(grid, 2, 2, "report.style.size.small", smallSize);
        return card("report.style.fonts.title", grid, compactRows, hint("report.style.fonts.hint"));
    }

    private Node headAndFootCard() {
        footerText.setPromptText(text("report.style.footer.prompt"));
        footerText.getStyleClass().add("modern-input");
        footerText.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().length() <= ReportStyle.MAX_FOOTER_LENGTH ? change : null));
        numbering.getStyleClass().add("modern-input");
        numbering.setMaxWidth(Double.MAX_VALUE);
        HBox numberingRow = new HBox(12, formLabel("report.style.numbering"), numbering);
        numberingRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(numbering, Priority.ALWAYS);
        return card("report.style.head.title", showLetterhead, showTitle, showSubtitle, showPrintedAt,
                showPrintedBy, showFooter, footerText, numberingRow);
    }

    private Node coloursCard() {
        palette.getStyleClass().add("modern-input");
        palette.setMaxWidth(Double.MAX_VALUE);
        HBox paletteRow = new HBox(12, formLabel("report.style.palette"), palette);
        paletteRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(palette, Priority.ALWAYS);
        return card("report.style.colours.title", paletteRow, inkSaver, hint("report.style.ink.saver.hint"));
    }

    private Node documentsCard() {
        HBox spaceRow = new HBox(12, formLabel("report.style.document.top.space"), documentTopSpace);
        spaceRow.setAlignment(Pos.CENTER_LEFT);
        return card("report.style.documents.title", showDocumentLetterhead, spaceRow,
                hint("report.style.document.hint"));
    }

    private Node previewPane() {
        previewReport.setText(text("report.style.preview.report"));
        previewDocument.setText(text("report.style.preview.document"));
        ToggleGroup group = new ToggleGroup();
        previewReport.setToggleGroup(group);
        previewDocument.setToggleGroup(group);
        previewReport.setSelected(true);
        // A toggle in a group clicked while selected deselects itself; the preview always shows one page.
        group.selectedToggleProperty().addListener((observable, before, after) -> {
            if (after == null) {
                before.setSelected(true);
            } else if (previewStarted) {
                renderPreview(read());
            }
        });
        Label title = new Label(text("report.style.preview.title"));
        title.getStyleClass().add("section-title");
        zoom.setText(text("report.style.preview.zoom"));
        zoom.setGraphic(AppIcon.SEARCH.graphic());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(8, title, spacer, zoom, previewReport, previewDocument);
        top.setAlignment(Pos.CENTER_LEFT);

        preview.setPreserveRatio(true);
        preview.setSmooth(true);
        StackPane page = new StackPane(preview);
        page.setPadding(new Insets(8));
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("settings-scroll-pane");
        // The whole page by default - the page number and the footer are what a style changes at its foot -
        // and the page's width on request, to judge a type size at the size it prints.
        scroll.viewportBoundsProperty().addListener((observable, before, bounds) -> fitPreview(bounds));
        zoom.selectedProperty().addListener((observable, before, after) -> fitPreview(scroll.getViewportBounds()));
        VBox.setVgrow(scroll, Priority.ALWAYS);

        previewStatus.getStyleClass().add("info-text");
        previewStatus.setWrapText(true);
        VBox pane = new VBox(8, top, scroll, previewStatus);
        pane.getStyleClass().add("app-card");
        pane.setMinWidth(300);
        HBox.setHgrow(pane, Priority.ALWAYS);
        return pane;
    }

    private void fitPreview(Bounds viewport) {
        double inset = 16;
        preview.setFitWidth(Math.max(100, viewport.getWidth() - inset));
        preview.setFitHeight(zoom.isSelected() ? 0 : Math.max(100, viewport.getHeight() - inset));
    }

    private VBox card(String titleKey, Node... content) {
        Label title = new Label(text(titleKey));
        title.getStyleClass().add("section-title");
        VBox card = new VBox(10, title);
        card.getChildren().addAll(content);
        card.getStyleClass().add("app-card");
        return card;
    }

    private void addSize(GridPane grid, int row, int column, String key, Spinner<Integer> spinner) {
        grid.add(formLabel(key), column, row);
        grid.add(spinner, column + 1, row);
    }

    private Label formLabel(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("form-label");
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    private Label hint(String key) {
        Label label = new Label(text(key));
        label.getStyleClass().add("settings-subtitle");
        label.setWrapText(true);
        return label;
    }

    private CheckBox check(String key) {
        CheckBox box = new CheckBox(text(key));
        box.getStyleClass().add("modern-check-box");
        box.setWrapText(true);
        return box;
    }

    private static ColumnConstraints grow() {
        ColumnConstraints column = new ColumnConstraints();
        column.setHgrow(Priority.SOMETIMES);
        return column;
    }

    private static Spinner<Integer> sizeSpinner() {
        return spinner(ReportStyle.MIN_FONT_SIZE, ReportStyle.MAX_FONT_SIZE, ReportStyle.MIN_FONT_SIZE, 1);
    }

    /**
     * A typable spinner that takes digits only while typing - ٠-٩ included, since {@code Integer.parseInt}
     * reads them - and goes back to the value it holds when what was typed is out of range.
     */
    private static Spinner<Integer> spinner(int min, int max, int initial, int step) {
        Spinner<Integer> spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initial, step));
        spinner.setPrefWidth(90);
        spinner.getStyleClass().add("modern-input");
        Utils.makeTypable(spinner);
        spinner.getEditor().setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("\\p{Nd}{0,3}") ? change : null));
        return spinner;
    }

    // ----------------------------------------------------------------------------- the choices

    private void configureControls() {
        numbering.getItems().setAll(PageNumbering.values());
        numbering.setCellFactory(list -> new NumberingCell());
        numbering.setButtonCell(new NumberingCell());
        palette.getItems().setAll(ReportPalette.values());
        palette.setCellFactory(list -> new PaletteCell());
        palette.setButtonCell(new PaletteCell());

        for (Spinner<Integer> spinner : List.of(titleSize, subtitleSize, headerSize, bodySize, totalsSize,
                smallSize, documentTopSpace)) {
            spinner.valueProperty().addListener((observable, before, after) -> changed());
        }
        for (CheckBox box : List.of(compactRows, showLetterhead, showTitle, showSubtitle, showPrintedAt,
                showPrintedBy, showFooter, inkSaver, showDocumentLetterhead)) {
            box.selectedProperty().addListener((observable, before, after) -> changed());
        }
        footerText.textProperty().addListener((observable, before, after) -> changed());
        numbering.valueProperty().addListener((observable, before, after) -> changed());
        palette.valueProperty().addListener((observable, before, after) -> changed());
    }

    /** Puts a style on the controls without taking it for a change somebody made. */
    private void show(ReportStyle style) {
        filling = true;
        try {
            titleSize.getValueFactory().setValue(style.titleSize());
            subtitleSize.getValueFactory().setValue(style.subtitleSize());
            headerSize.getValueFactory().setValue(style.headerSize());
            bodySize.getValueFactory().setValue(style.bodySize());
            totalsSize.getValueFactory().setValue(style.totalsSize());
            smallSize.getValueFactory().setValue(style.smallSize());
            compactRows.setSelected(style.compactRows());
            showLetterhead.setSelected(style.showLetterhead());
            showTitle.setSelected(style.showTitle());
            showSubtitle.setSelected(style.showSubtitle());
            showPrintedAt.setSelected(style.showPrintedAt());
            showPrintedBy.setSelected(style.showPrintedBy());
            showFooter.setSelected(style.showFooter());
            footerText.setText(style.footerText());
            numbering.setValue(style.pageNumbering());
            palette.setValue(style.palette());
            inkSaver.setSelected(style.inkSaver());
            showDocumentLetterhead.setSelected(style.showDocumentLetterhead());
            documentTopSpace.getValueFactory().setValue(style.documentTopSpaceMm());
        } finally {
            filling = false;
        }
        enableWhatApplies();
    }

    private ReportStyle read() {
        return ReportStyle.DEFAULT.toBuilder()
                .titleSize(titleSize.getValue())
                .subtitleSize(subtitleSize.getValue())
                .headerSize(headerSize.getValue())
                .bodySize(bodySize.getValue())
                .totalsSize(totalsSize.getValue())
                .smallSize(smallSize.getValue())
                .compactRows(compactRows.isSelected())
                .showLetterhead(showLetterhead.isSelected())
                .showTitle(showTitle.isSelected())
                .showSubtitle(showSubtitle.isSelected())
                .showPrintedAt(showPrintedAt.isSelected())
                .showPrintedBy(showPrintedBy.isSelected())
                .showFooter(showFooter.isSelected())
                .footerText(footerText.getText())
                .pageNumbering(numbering.getValue())
                .palette(palette.getValue())
                .inkSaver(inkSaver.isSelected())
                .showDocumentLetterhead(showDocumentLetterhead.isSelected())
                .documentTopSpaceMm(documentTopSpace.getValue())
                .build();
    }

    /** The footer's text means nothing with the footer off, and the space above a document nothing with its letterhead on. */
    private void enableWhatApplies() {
        footerText.setDisable(!showFooter.isSelected());
        documentTopSpace.setDisable(showDocumentLetterhead.isSelected());
    }

    private void changed() {
        if (filling) {
            return;
        }
        enableWhatApplies();
        settle.playFromStart();
    }

    private void apply() {
        ReportStyle style = read();
        if (!style.equals(saved)) {
            try {
                ShopReportSetup.store(style);
                saved = style;
            } catch (RuntimeException e) {
                log.error("The report style could not be saved", e);
                previewStatus.setText(text("report.style.save.failed"));
                return;
            }
        }
        previewStarted = true;
        renderPreview(style);
    }

    // ----------------------------------------------------------------------------- the preview

    private void renderPreview(ReportStyle style) {
        long ticket = generation.incrementAndGet();
        boolean document = previewDocument.isSelected();
        previewStatus.setText(text("report.style.preview.loading"));
        Task<Image> task = new Task<>() {
            @Override
            protected Image call() throws Exception {
                return drawPage(style, document);
            }
        };
        task.setOnSucceeded(event -> {
            if (ticket == generation.get()) {
                preview.setImage(task.getValue());
                previewStatus.setText(text("report.style.preview.hint"));
            }
        });
        task.setOnFailed(event -> {
            log.warn("The report style preview could not be drawn", task.getException());
            if (ticket == generation.get()) {
                previewStatus.setText(text("report.style.preview.failed"));
            }
        });
        TablePdfReport.start(task, "report-style-preview");
    }

    /** Writes the sample page as a report would be written, and draws its first page back. */
    private Image drawPage(ReportStyle style, boolean document) throws Exception {
        ReportSetup base = previewBase;
        if (base == null) {
            base = ShopReportSetup.forPreview(style);
            previewBase = base;
        }
        ReportSetup setup = base.withStyle(style);
        PdfExportService service = new PdfExportService(setup);
        ReportStyleSample.Labels labels = language::getString;
        File file = Files.createTempFile("report-style-preview-", ".pdf").toFile();
        try {
            boolean written;
            if (document) {
                written = service.exportDocument(file.getAbsolutePath(),
                        ReportStyleSample.document(labels, setup.letterhead(), LocalDate.now(),
                                Columns.DATE_TIME.format(LocalDateTime.now())),
                        TablePdfReport.uprightPageSize());
            } else {
                ReportStyleSample.SampleReport sample = ReportStyleSample.report(labels, LocalDate.now());
                written = service.exportGroupedReport(file.getAbsolutePath(), sample.title(), sample.subtitle(),
                        sample.headers(), sample.columnWidths(), sample.rows(), sample.totals(),
                        TablePdfReport.uprightPageSize());
            }
            if (!written) {
                throw new IllegalStateException("The sample page was not written");
            }
            try (PDDocument pdf = Loader.loadPDF(file)) {
                return SwingFXUtils.toFXImage(new PDFRenderer(pdf).renderImage(0, PREVIEW_SCALE), null);
            }
        } finally {
            if (!file.delete()) {
                file.deleteOnExit();
            }
        }
    }

    private String text(String key) {
        return language.getString(key);
    }

    // ----------------------------------------------------------------------------- cells

    /**
     * {@code 1 / 5} is set between two left-to-right marks: in a right-to-left list the bidi rules would
     * draw it {@code 5 / 1}, while the page prints it as written.
     */
    private final class NumberingCell extends ListCell<PageNumbering> {
        @Override
        protected void updateItem(PageNumbering item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                return;
            }
            setText(switch (item) {
                case NONE -> text("report.style.numbering.none");
                case SLASH -> "‎" + item.text(1, 5, "") + "‎";
                case PAGE_OF_TOTAL -> item.text(1, 5, text("report.pdf.page.of"));
            });
        }
    }

    /** A palette by its name and a swatch of its heading and its band, which is what it changes on the page. */
    private final class PaletteCell extends ListCell<ReportPalette> {
        @Override
        protected void updateItem(ReportPalette item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(switch (item) {
                case BLUE -> text("report.style.palette.blue");
                case GREEN -> text("report.style.palette.green");
                case CHARCOAL -> text("report.style.palette.charcoal");
                case MAROON -> text("report.style.palette.maroon");
                case PURPLE -> text("report.style.palette.purple");
            });
            HBox swatch = new HBox(2, swatch(item.heading()), swatch(item.band()));
            setGraphic(swatch);
        }

        private Rectangle swatch(int rgb) {
            Rectangle rectangle = new Rectangle(16, 12,
                    Color.rgb((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF));
            rectangle.setStroke(Color.gray(0.6));
            return rectangle;
        }
    }
}
