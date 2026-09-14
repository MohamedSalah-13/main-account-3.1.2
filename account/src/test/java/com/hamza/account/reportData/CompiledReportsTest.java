package com.hamza.account.reportData;

import net.sf.jasperreports.engine.JasperReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CompiledReportsTest {

    @TempDir
    Path dir;

    private static String template(String name) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <jasperReport xmlns="http://jasperreports.sourceforge.net/jasperreports"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://jasperreports.sourceforge.net/jasperreports http://jasperreports.sourceforge.net/xsd/jasperreport.xsd"
                    name="%s" pageWidth="200" pageHeight="200" columnWidth="200" leftMargin="0" rightMargin="0"
                    topMargin="0" bottomMargin="0" uuid="0c9e3f4a-1b2c-4d5e-8f60-718293a4b5c6">
                </jasperReport>
                """.formatted(name);
    }

    /** The whole point: a print after the first does not compile the template again. */
    @Test
    void anUnchangedTemplateIsCompiledOnce() throws Exception {
        Path file = dir.resolve("once.jrxml");
        Files.writeString(file, template("Once"), StandardCharsets.UTF_8);

        JasperReport first = CompiledReports.file(file.toString());
        JasperReport second = CompiledReports.file(file.toString());

        assertSame(first, second);
        assertEquals("Once", first.getName());
    }

    /** reports/ is edited beside the installed program; an edit must show without a restart. */
    @Test
    void anEditedTemplateIsCompiledAgain() throws Exception {
        Path file = dir.resolve("edited.jrxml");
        Files.writeString(file, template("Before"), StandardCharsets.UTF_8);
        JasperReport before = CompiledReports.file(file.toString());

        Files.writeString(file, template("AfterTheEdit"), StandardCharsets.UTF_8);
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(
                Files.getLastModifiedTime(file).toMillis() + 5_000));
        JasperReport after = CompiledReports.file(file.toString());

        assertNotSame(before, after);
        assertEquals("AfterTheEdit", after.getName());
    }
}
