package com.hamza.account.reportData;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.xml.JRXmlLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@code .jrxml} compiled once and reused, instead of on every print.
 * <p>
 * {@link JasperData} loaded and compiled the template each time it printed, so every sale paid for
 * it: measured on the 80mm receipt, 1.3 s for the first print after the program opens and 110-210 ms
 * for each one after, before a single field was filled (the fill itself takes about 40 ms). A compiled
 * {@link JasperReport} is not changed by filling it, so one instance serves every print.
 * <p>
 * <b>A template on disk is recompiled when its file changes.</b> The {@code reports/} folder sits
 * beside the installed program and is edited in the field; a cache keyed on the path alone would keep
 * printing the old layout until the program was restarted. A template packaged as a resource cannot
 * change while the program runs, so it is compiled once.
 */
public final class CompiledReports {

    private record Compiled(long lastModified, long length, JasperReport report) {
    }

    private static final Map<String, Compiled> FILES = new ConcurrentHashMap<>();
    private static final Map<String, JasperReport> RESOURCES = new ConcurrentHashMap<>();

    private CompiledReports() {
    }

    /** The template at this path, compiled; compiled again only when the file has changed. */
    public static JasperReport file(String path) throws JRException {
        File file = new File(path);
        long lastModified = file.lastModified();
        long length = file.length();
        Compiled cached = FILES.get(path);
        if (cached != null && cached.lastModified() == lastModified && cached.length() == length) {
            return cached.report();
        }
        JasperReport report = JasperCompileManager.compileReport(JRXmlLoader.load(path));
        FILES.put(path, new Compiled(lastModified, length, report));
        return report;
    }

    /** The template packaged at this class-path resource, compiled once. */
    public static JasperReport resource(String resourcePath) throws JRException {
        JasperReport cached = RESOURCES.get(resourcePath);
        if (cached != null) {
            return cached;
        }
        try (InputStream input = CompiledReports.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new JRException("Packaged report resource was not found: " + resourcePath);
            }
            JasperReport report = JasperCompileManager.compileReport(JRXmlLoader.load(input));
            RESOURCES.put(resourcePath, report);
            return report;
        } catch (IOException e) {
            throw new JRException("Could not close packaged report resource: " + resourcePath, e);
        }
    }
}
