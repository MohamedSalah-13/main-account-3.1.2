package com.hamza.account.reportData;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Every {@code .jrxml} template, compiled - nothing more, since none of these carry a
 * dedicated Java test the way the DAOs and services do (see {@code CLAUDE.md}: "the
 * reports" are one of the pieces the test suite has never covered).
 * <p>
 * Hand-authored XML has no compiler to catch a typo the way Java does; a field name
 * that does not match its bean's getter, or a schema violation, surfaces only the
 * moment someone actually presses "طباعة" on a live screen. This at least fails the
 * build instead, the same day the file is broken - not the day a shop needs to print.
 * <p>
 * Compiling is not filling: a wrong field name that no live column happens to touch
 * (an unused parameter, a column nobody scrolls to) would still pass here. What this
 * catches is a malformed report the printing code can never reach in the first place.
 */
class ReportTemplatesCompileTest {

    private static final File REPORTS_DIR = resolveReportsDir();

    private static File resolveReportsDir() {
        File fromModule = new File("../reports/ar");
        if (fromModule.isDirectory()) return fromModule;
        File fromRoot = new File("reports/ar");
        if (fromRoot.isDirectory()) return fromRoot;
        throw new IllegalStateException("Cannot find the reports/ar directory from " + new File(".").getAbsolutePath());
    }

    @TestFactory
    Stream<DynamicTest> everyJrxmlCompiles() {
        FileFilter jrxml = file -> file.getName().endsWith(".jrxml");
        File[] files = REPORTS_DIR.listFiles(jrxml);
        List<File> templates = files == null ? List.of() : Arrays.stream(files).sorted().toList();
        return templates.stream().map(file -> DynamicTest.dynamicTest(file.getName(), () ->
                assertDoesNotThrow(() -> compile(file), file.getName() + " failed to compile")));
    }

    private static void compile(File file) throws JRException {
        JasperDesign design = JRXmlLoader.load(file.getAbsolutePath());
        JasperCompileManager.compileReport(design);
    }
}
