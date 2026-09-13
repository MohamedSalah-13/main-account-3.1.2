package com.hamza.account.reportData;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ShiftReportPackagedResourcesTest {

    @ParameterizedTest
    @ValueSource(strings = {
            JasperReportPaths.Shift.X_REPORT_80_RESOURCE,
            JasperReportPaths.Shift.Z_REPORT_80_RESOURCE
    })
    void shiftTemplateIsPackagedAndCompiles(String resourcePath) {
        assertDoesNotThrow(() -> {
            try (InputStream input = ShiftReportPackagedResourcesTest.class.getResourceAsStream(resourcePath)) {
                assertNotNull(input, resourcePath);
                JasperDesign design = JRXmlLoader.load(input);
                JasperCompileManager.compileReport(design);
            }
        });
    }
}
