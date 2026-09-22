package com.hamza.account.reportData;

import com.hamza.account.features.shift.ShiftReportLayout;
import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.account.model.domain.UserShift;
import com.hamza.account.service.ShiftReportService.ShiftReportData;
import com.hamza.account.service.ShiftReportService.ShiftReportType;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintFrame;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shift template, filled with what {@link Print_Reports#shiftReportParameters} and
 * {@link ShiftReportLayout#rows()} hand it.
 * <p>
 * The two templates this replaced compiled perfectly and printed an Arabic paper laid out as if it were
 * English, with its amounts in Arabic-Indic digits beside a shift number in Latin ones. Compiling says
 * nothing about either, so this fills the real file with an Arabic report locale - the one the program
 * passes - and reads back the text and where it landed.
 */
class ShiftReportTemplateFillTest {

    private static final File TEMPLATE = resolve();

    private static File resolve() {
        File fromModule = new File("../reports/ar/shift-report-80mm.jrxml");
        return fromModule.isFile() ? fromModule : new File("reports/ar/shift-report-80mm.jrxml");
    }

    private static ShiftReportLayout layout(ShiftReportType type) {
        UserShift shift = new UserShift();
        shift.setId(2);
        shift.setUsername("admin");
        shift.setTreasuryName("treasury");
        shift.setOpenTime(LocalDateTime.of(2026, 9, 22, 18, 5, 37));
        shift.setCloseTime(type == ShiftReportType.Z ? LocalDateTime.of(2026, 9, 22, 18, 6, 37) : null);
        shift.setOpenBalance(new BigDecimal("10"));
        shift.setCloseBalance(new BigDecimal("1255.5"));
        ShiftSummary summary = ShiftSummary.builder()
                .openBalance(new BigDecimal("10"))
                .totalSales(new BigDecimal("1250.5"))
                .totalIn(new BigDecimal("1250.5"))
                .totalExpenses(new BigDecimal("5"))
                .totalOut(new BigDecimal("5"))
                .invoicesCount(3)
                .build();
        return ShiftReportLayout.of(new ShiftReportData(shift, summary, LocalDateTime.now(), type, true),
                LocalDateTime.of(2026, 9, 22, 18, 8, 48), "admin", key -> key);
    }

    private static JasperPrint fill(ShiftReportLayout layout) throws Exception {
        HashMap<String, Object> parameters = Print_Reports.shiftReportParameters(layout);
        parameters.put("compName", "company");
        parameters.put("REPORT_LOCALE", Locale.forLanguageTag("ar"));
        return JasperFillManager.fillReport(CompiledReports.file(TEMPLATE.getPath()), parameters,
                new JRBeanCollectionDataSource(layout.rows()));
    }

    private static List<JRPrintText> texts(JasperPrint print) {
        List<JRPrintText> texts = new ArrayList<>();
        print.getPages().forEach(page -> collect(page.getElements(), texts));
        return texts;
    }

    private static void collect(List<JRPrintElement> elements, List<JRPrintText> texts) {
        for (JRPrintElement element : elements) {
            if (element instanceof JRPrintText text) {
                texts.add(text);
            } else if (element instanceof JRPrintFrame frame) {
                collect(frame.getElements(), texts);
            }
        }
    }

    private static List<String> strings(JasperPrint print) {
        return texts(print).stream().map(JRPrintText::getFullText).toList();
    }

    private static JRPrintText find(JasperPrint print, String text) {
        return texts(print).stream().filter(element -> text.equals(element.getFullText())).findFirst()
                .orElseThrow(() -> new AssertionError(text + " not printed: " + strings(print)));
    }

    @Test
    void theAmountsArePrintedAsWrittenInLatinDigitsUnderAnArabicLocale() throws Exception {
        JasperPrint print = fill(layout(ShiftReportType.Z));
        List<String> strings = strings(print);

        assertTrue(strings.contains("1,250.50"), strings.toString());
        assertTrue(strings.contains("1,255.50"), "the expected balance, 10 + 1,250.50 - 5: " + strings);
        assertTrue(strings.contains("10.00"), strings.toString());
        assertFalse(strings.stream().anyMatch(text -> text != null
                        && text.chars().anyMatch(c -> c >= '٠' && c <= '٩')),
                "no Arabic-Indic digit anywhere on the paper: " + strings);
    }

    /** Right to left: every label sits to the right of its figure. */
    @Test
    void eachLabelIsToTheRightOfItsFigure() throws Exception {
        JasperPrint print = fill(layout(ShiftReportType.Z));

        JRPrintText label = find(print, "user.shift.report.row.opening");
        JRPrintText value = find(print, "10.00");
        assertEquals(label.getY(), value.getY(), "one row");
        assertTrue(label.getX() > value.getX() + value.getWidth() - 1,
                "label at x=" + label.getX() + ", figure ends at x=" + (value.getX() + value.getWidth()));
    }

    @Test
    void theZReportIsSignedAndTheXReportIsNot() throws Exception {
        List<String> z = strings(fill(layout(ShiftReportType.Z)));
        List<String> x = strings(fill(layout(ShiftReportType.X)));

        assertTrue(z.stream().anyMatch(text -> text.startsWith("user.shift.report.signature.cashier")), z.toString());
        assertTrue(x.stream().noneMatch(text -> text.startsWith("user.shift.report.signature")), x.toString());
        assertTrue(x.stream().noneMatch(text -> text.startsWith("user.shift.report.row.counted")), x.toString());
    }

    /** One page, as long as what is on it: a report is a strip of paper, not an A4 sheet. */
    @Test
    void theReportIsOnePageAsLongAsItsContent() throws Exception {
        JasperPrint z = fill(layout(ShiftReportType.Z));
        JasperPrint x = fill(layout(ShiftReportType.X));

        assertEquals(1, z.getPages().size());
        assertTrue(z.getPageHeight() < 700, "Z: " + z.getPageHeight());
        assertTrue(x.getPageHeight() < z.getPageHeight(),
                "the X report has no count, no difference and no signatures: " + x.getPageHeight());
    }

    /**
     * Jasper reads the rows through commons-beanutils, which may call their getters only if the
     * row's package is exported. This test fills the template on the class path, where modules are
     * not enforced, so it passed while the program - launched as a module - refused to print the
     * first X report it was asked for.
     */
    @Test
    void theRowsPackageIsExportedToTheModuleThatReadsIt() throws Exception {
        File moduleInfo = new File(TEMPLATE.getParentFile().getParentFile().getParentFile(),
                "account/src/main/java/module-info.java");
        String source = java.nio.file.Files.readString(moduleInfo.toPath());

        assertTrue(source.contains("exports " + ShiftReportLayout.Row.class.getPackageName() + ";"),
                "module-info.java must export " + ShiftReportLayout.Row.class.getPackageName());
    }

    /**
     * No two printed rows overlap. The old template drew the treasury row across the rule under it,
     * which printed the treasury's name struck through.
     */
    @Test
    void noTwoRowsOverlap() throws Exception {
        List<JRPrintText> texts = texts(fill(layout(ShiftReportType.Z)));

        for (int i = 0; i < texts.size(); i++) {
            for (int j = i + 1; j < texts.size(); j++) {
                JRPrintText a = texts.get(i);
                JRPrintText b = texts.get(j);
                boolean horizontally = a.getX() < b.getX() + b.getWidth() && b.getX() < a.getX() + a.getWidth();
                boolean vertically = a.getY() < b.getY() + b.getHeight() && b.getY() < a.getY() + a.getHeight();
                assertFalse(horizontally && vertically,
                        "'" + a.getFullText() + "' overlaps '" + b.getFullText() + "'");
            }
        }
    }
}
