package com.hamza.account.reportData;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The shift template is printed from the class path, so it has to be packaged there. */
class ShiftReportPackagedResourcesTest {

    @Test
    void shiftTemplateIsPackagedAndCompiles() {
        String resourcePath = JasperReportPaths.Shift.REPORT_80_RESOURCE;
        assertDoesNotThrow(() -> {
            try (InputStream input = ShiftReportPackagedResourcesTest.class.getResourceAsStream(resourcePath)) {
                assertNotNull(input, resourcePath);
                JasperDesign design = JRXmlLoader.load(input);
                JasperCompileManager.compileReport(design);
            }
        });
    }
}
