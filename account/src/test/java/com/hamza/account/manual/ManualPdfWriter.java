package com.hamza.account.manual;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.BaseDirection;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.Property;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The manual as a PDF: right to left, Arabic shaped a line at a time, with a contents list whose
 * page numbers are the pages the reader will actually turn to.
 * <p>
 * <b>Every paragraph is wrapped here and shaped per line</b> - see {@link ManualText} for what
 * happens when it is not. Nothing in this class hands iText a whole paragraph to break.
 * <p>
 * <b>The contents list is produced by rendering the document twice.</b> A page number is only known
 * once the pages before it exist, and the contents list is itself pages. The first pass writes the
 * real document behind a contents list of the right <em>length</em> but with no numbers in it; the
 * numbers it records are therefore already the final ones, because the second pass fills that same
 * list in place without changing how many pages it takes. Working it out from a single pass and
 * adding an offset is the version that is wrong whenever the list crosses a page boundary.
 */
public final class ManualPdfWriter {

    private static final String REGULAR = "/com/hamza/account/fonts/NotoNaskhArabic-Regular.ttf";
    private static final String BOLD = "/com/hamza/account/fonts/NotoNaskhArabic-Bold.ttf";

    private static final DeviceRgb INK = new DeviceRgb(33, 37, 41);
    private static final DeviceRgb ACCENT = new DeviceRgb(41, 128, 185);
    private static final DeviceRgb MUTED = new DeviceRgb(108, 117, 125);
    private static final DeviceRgb WARN_INK = new DeviceRgb(146, 43, 33);
    private static final DeviceRgb NOTE_BG = new DeviceRgb(232, 244, 253);
    private static final DeviceRgb WARN_BG = new DeviceRgb(253, 237, 236);
    private static final DeviceRgb TIP_BG = new DeviceRgb(234, 248, 239);
    private static final DeviceRgb FACT_BG = new DeviceRgb(245, 246, 247);
    private static final DeviceRgb MISSING_BG = new DeviceRgb(252, 248, 227);
    private static final DeviceRgb MISSING_INK = new DeviceRgb(126, 99, 16);
    private static final DeviceRgb HAIRLINE = new DeviceRgb(222, 226, 230);

    private static final float MARGIN = 48f;
    private static final float BODY = 11.5f;
    private static final float LEADING = 1.75f;
    /** How many contents rows fit on one page. Only the count matters - see the class note. */
    private static final int CONTENTS_ROWS_PER_PAGE = 26;

    /**
     * The font <b>bytes</b> are held, not the fonts. A {@code PdfFont} belongs to the document it
     * was made for, and reusing one across the two passes fails at close with "Pdf indirect object
     * belongs to other PDF document" - after the whole file has been written.
     */
    private final byte[] regularBytes;
    private final byte[] boldBytes;

    private PdfFont regular;
    private PdfFont bold;

    public ManualPdfWriter() throws IOException {
        this.regularBytes = fontBytes(REGULAR);
        this.boldBytes = fontBytes(BOLD);
    }

    /** @return the page each page id starts on, which is also what the contents list printed */
    public Map<String, Integer> write(List<ManualPage> pages, ManualMeta meta, Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        // Pass one: the numbers. Written to memory and thrown away.
        Map<String, Integer> numbers = render(pages, meta, Map.of(), new ByteArrayOutputStream());
        // Pass two: the same document, with the numbers now in the contents list.
        try (OutputStream out = Files.newOutputStream(target)) {
            return render(pages, meta, numbers, out);
        }
    }

    private Map<String, Integer> render(List<ManualPage> pages, ManualMeta meta,
                                        Map<String, Integer> known, OutputStream out) throws IOException {
        Map<String, Integer> numbers = new LinkedHashMap<>();
        PdfDocument pdf = new PdfDocument(new PdfWriter(out));
        regular = font(regularBytes);
        bold = font(boldBytes);
        // immediateFlush=false: the footers are drawn on every page after the body is laid out,
        // and a page iText has already flushed has no dictionary left to draw on.
        Document document = new Document(pdf, PageSize.A4, false);
        document.setFont(regular);
        document.setFontSize(BODY);
        document.setFontColor(INK);
        document.setProperty(Property.BASE_DIRECTION, BaseDirection.RIGHT_TO_LEFT);
        document.setTextAlignment(TextAlignment.RIGHT);
        document.setMargins(MARGIN, MARGIN, MARGIN + 18, MARGIN);

        cover(document, meta);
        contents(document, pages, known);

        String chapter = null;
        for (ManualPage page : pages) {
            document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
            if (!page.chapter().equals(chapter)) {
                chapter = page.chapter();
                document.add(line(chapter, bold, 12.5f, ACCENT).setMarginBottom(2));
            }
            numbers.put(page.id(), pdf.getNumberOfPages());
            document.add(line(page.title(), bold, 19f, INK).setMarginBottom(10));
            for (ManualBlock block : page.blocks()) {
                add(document, block, meta);
            }
        }
        footers(pdf, meta);
        document.close();
        return numbers;
    }

    // ---------------------------------------------------------------- the front matter

    private void cover(Document document, ManualMeta meta) {
        document.add(new Paragraph(" ").setMarginTop(150).setFontSize(1));
        document.add(line(meta.productName(), bold, 34f, ACCENT).setTextAlignment(TextAlignment.CENTER));
        document.add(line("دليل المستخدم", bold, 22f, INK)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(6));
        document.add(line("شرح الشاشات والعمليات اليومية", regular, 13f, MUTED)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(14));
        document.add(line("الإصدار `" + meta.version() + "`", regular, 12f, INK)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(60));
        document.add(line(meta.date().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), regular, 12f, MUTED)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(4));
    }

    private void contents(Document document, List<ManualPage> pages, Map<String, Integer> known) {
        document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
        document.add(line("المحتويات", bold, 22f, INK).setMarginBottom(14));

        String chapter = null;
        int rows = 0;
        for (ManualPage page : pages) {
            if (!page.chapter().equals(chapter)) {
                chapter = page.chapter();
                document.add(line(chapter, bold, 13f, ACCENT)
                        .setMarginTop(rows == 0 ? 0 : 10).setMarginBottom(2));
                rows++;
            }
            document.add(contentsRow(page.title(), known.get(page.id())));
            rows++;
        }
        // The list has to occupy the same number of pages in both passes, whatever it holds.
        for (int filler = rows; filler % CONTENTS_ROWS_PER_PAGE != 0; filler++) {
            document.add(new Paragraph(" ").setFontSize(BODY).setMargin(0).setFixedLeading(BODY * LEADING));
        }
    }

    private Paragraph contentsRow(String title, Integer page) {
        // A dotted leader would have to be measured against the shaped title; the number being on
        // the correct side is the part that has to be right, so the row is kept plain.
        String text = page == null ? title : title + " ......... `" + page + "`";
        return line(text, regular, BODY, INK).setMarginBottom(1);
    }

    // ---------------------------------------------------------------- the blocks

    private void add(Document document, ManualBlock block, ManualMeta meta) {
        switch (block) {
            case ManualBlock.Heading heading -> document.add(
                    line(heading.text(), bold, heading.level() == 2 ? 15f : 13f,
                            heading.level() == 2 ? ACCENT : INK)
                            .setMarginTop(heading.level() == 2 ? 16 : 11)
                            .setMarginBottom(4));
            case ManualBlock.Para para -> paragraph(document, para.text(), 0f, null);
            case ManualBlock.Bullets bullets -> {
                for (String item : bullets.items()) {
                    paragraph(document, item, 16f, "•");
                }
            }
            case ManualBlock.Steps steps -> {
                int number = 1;
                for (String item : steps.items()) {
                    paragraph(document, item, 20f, "`" + number++ + ".`");
                }
            }
            case ManualBlock.Callout callout -> document.add(callout(callout));
            case ManualBlock.Facts facts -> document.add(facts(facts));
            case ManualBlock.Figure figure -> figure(document, figure, meta);
        }
    }

    /**
     * A wrapped, shaped paragraph. The marker - a bullet or a step number - is put on the first
     * line only and every following line is indented past it, so a wrapped item reads as one item
     * rather than as a new one.
     */
    private void paragraph(Document document, String text, float inset, String marker) {
        float hanging = marker == null ? 0f : markerWidth(marker);
        float width = PageSize.A4.getWidth() - 2 * MARGIN - inset - hanging;
        List<List<ManualText.Span>> lines = ManualText.wrap(text, width, runs -> measure(runs, BODY));
        for (int index = 0; index < lines.size(); index++) {
            boolean first = index == 0;
            Paragraph rendered = spanLine(lines.get(index), BODY, INK, first ? marker : null)
                    .setMarginRight(first ? inset : inset + hanging);
            if (index == lines.size() - 1) {
                rendered.setMarginBottom(marker == null ? 7 : 3);
            }
            document.add(rendered);
        }
    }

    private float markerWidth(String marker) {
        return Math.min(22f, regular.getWidth(ManualText.shape(marker + " "), BODY));
    }

    private Div callout(ManualBlock.Callout callout) {
        DeviceRgb background = switch (callout.kind()) {
            case NOTE -> NOTE_BG;
            case WARNING -> WARN_BG;
            case TIP -> TIP_BG;
        };
        String label = switch (callout.kind()) {
            case NOTE -> "ملاحظة";
            case WARNING -> "تحذير";
            case TIP -> "نصيحة";
        };
        DeviceRgb labelInk = callout.kind() == ManualBlock.Callout.Kind.WARNING ? WARN_INK : ACCENT;
        Div box = new Div()
                .setBackgroundColor(background)
                .setKeepTogether(true)
                .setPadding(9)
                .setMarginTop(7)
                .setMarginBottom(9)
                .setWidth(UnitValue.createPercentValue(100));
        box.add(line(label, bold, 11f, labelInk).setMarginBottom(2));
        float width = PageSize.A4.getWidth() - 2 * MARGIN - 24;
        for (List<ManualText.Span> runs : ManualText.wrap(callout.text(), width, line -> measure(line, BODY))) {
            box.add(spanLine(runs, BODY, INK, null));
        }
        return box;
    }

    private Div facts(ManualBlock.Facts facts) {
        Div box = new Div()
                .setBackgroundColor(FACT_BG)
                .setPadding(9)
                .setMarginBottom(10)
                .setWidth(UnitValue.createPercentValue(100));
        for (ManualBlock.Facts.Fact fact : facts.rows()) {
            // The value goes through the same inline reading as any other line: a fact whose
            // value names two choices in bold printed its asterisks otherwise.
            List<ManualText.Span> runs = new ArrayList<>();
            runs.add(new ManualText.Span(fact.label() + ": ", true));
            runs.addAll(ManualText.spans(fact.value()));
            box.add(spanLine(runs, 10.5f, INK, null).setMarginBottom(1));
        }
        return box;
    }

    private void figure(Document document, ManualBlock.Figure figure, ManualMeta meta) {
        Path file = meta.imagesDir().resolve(figure.imageId() + ".png");
        if (!Files.isRegularFile(file)) {
            document.add(missingPicture(figure));
            return;
        }
        try {
            Image image = new Image(ImageDataFactory.create(file.toAbsolutePath().toString()));
            image.setAutoScale(true);
            image.setMaxWidth(PageSize.A4.getWidth() - 2 * MARGIN);
            image.setHorizontalAlignment(HorizontalAlignment.CENTER);
            image.setBorder(new SolidBorder(HAIRLINE, 0.75f));
            image.setMarginTop(8);
            document.add(image);
        } catch (RuntimeException | IOException failure) {
            document.add(missingPicture(figure));
            return;
        }
        if (!figure.caption().isBlank()) {
            document.add(line(figure.caption(), regular, 9.5f, MUTED)
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(3).setMarginBottom(10));
        }
    }

    /**
     * A picture that has not been captured is drawn as a box that says so, naming the id the
     * capture harness answers to. A manual that silently omits a figure reads as finished.
     */
    private Div missingPicture(ManualBlock.Figure figure) {
        Div box = new Div()
                .setBackgroundColor(MISSING_BG)
                .setBorder(new SolidBorder(new DeviceRgb(214, 178, 62), 0.75f))
                .setPadding(14)
                .setMarginTop(8)
                .setMarginBottom(10)
                .setWidth(UnitValue.createPercentValue(100));
        box.add(line("لم تُلتقط صورة هذه الشاشة بعد", bold, 11f, MISSING_INK)
                .setTextAlignment(TextAlignment.CENTER));
        box.add(line("`" + figure.imageId() + ".png`", regular, 9.5f, MUTED)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(2));
        return box;
    }

    // ---------------------------------------------------------------- the furniture

    private void footers(PdfDocument pdf, ManualMeta meta) {
        int total = pdf.getNumberOfPages();
        for (int number = 2; number <= total; number++) {
            Rectangle box = new Rectangle(MARGIN, 22, PageSize.A4.getWidth() - 2 * MARGIN, 24);
            try (Canvas canvas = new Canvas(new PdfCanvas(pdf.getPage(number)), box)) {
                canvas.setProperty(Property.BASE_DIRECTION, BaseDirection.RIGHT_TO_LEFT);
                canvas.add(line(meta.productName() + " — دليل المستخدم — إصدار `" + meta.version() + "`"
                                + "          `" + number + "`",
                        regular, 8.5f, MUTED).setTextAlignment(TextAlignment.RIGHT).setMargin(0));
            }
        }
    }

    /** One already-fitting line: prepared, shaped, and given the face that can draw it. */
    private Paragraph line(String logical, PdfFont face, float size, DeviceRgb colour) {
        String shaped = ManualText.shape(logical);
        return new Paragraph(shaped)
                .setFont(faceFor(shaped, face))
                .setFontSize(size)
                .setFontColor(colour)
                .setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
                .setTextAlignment(TextAlignment.RIGHT)
                .setMargin(0)
                .setFixedLeading(size * LEADING)
                .setBorder(Border.NO_BORDER);
    }

    /**
     * One already-fitting line built from its runs.
     * <p>
     * <b>The runs go in reversed.</b> Each is shaped on its own, so each is already in visual
     * order; the line is then assembled right to left, which puts the first logical run at the
     * right edge. Added in logical order the bold lead-in of a bullet landed mid-line with the
     * colon after it thrown to the far end - see {@link ManualText}.
     */
    private Paragraph spanLine(List<ManualText.Span> runs, float size, DeviceRgb colour, String marker) {
        List<ManualText.Span> ordered = new ArrayList<>(runs);
        if (marker != null) {
            ordered.addFirst(new ManualText.Span(marker + " ", false));
        }
        Collections.reverse(ordered);
        Paragraph paragraph = new Paragraph()
                .setFontSize(size)
                .setFontColor(colour)
                .setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
                .setTextAlignment(TextAlignment.RIGHT)
                .setMargin(0)
                .setFixedLeading(size * LEADING)
                .setBorder(Border.NO_BORDER);
        for (ManualText.Span run : ordered) {
            String shaped = ManualText.shape(run.text());
            paragraph.add(new Text(shaped).setFont(faceFor(shaped, run.bold() ? bold : regular)));
        }
        return paragraph;
    }

    /**
     * The asked-for face, unless it cannot draw one of the characters - then the regular one.
     * The bundled bold Naskh has no glyph for the hyphen-minus, and a bold line containing one
     * printed an empty box in its place; {@code PdfExportService.boldFontFor} carries the same
     * guard for the same reason.
     */
    private PdfFont faceFor(String shaped, PdfFont wanted) {
        return shaped.codePoints().allMatch(wanted::containsGlyph) ? wanted : regular;
    }

    /**
     * A line's width, each run measured in the face it will be drawn with. The bold face is wider,
     * so measuring a line holding a bold term in the regular face under-reads it - see
     * {@link ManualText} for what iText then does with a line that does not fit.
     */
    private float measure(List<ManualText.Span> runs, float size) {
        float width = 0f;
        for (ManualText.Span run : runs) {
            String shaped = ManualText.shape(run.text());
            width += faceFor(shaped, run.bold() ? bold : regular).getWidth(shaped, size);
        }
        return width;
    }

    private static byte[] fontBytes(String resource) throws IOException {
        try (InputStream in = ManualPdfWriter.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Manual font not on the classpath: " + resource);
            }
            return in.readAllBytes();
        }
    }

    private static PdfFont font(byte[] bytes) throws IOException {
        return PdfFontFactory.createFont(bytes, PdfEncodings.IDENTITY_H,
                PdfFontFactory.EmbeddingStrategy.FORCE_EMBEDDED);
    }
}
