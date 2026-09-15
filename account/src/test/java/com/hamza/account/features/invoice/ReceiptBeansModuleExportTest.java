package com.hamza.account.features.invoice;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The receipt template reads {@link InvoiceReceiptLayout.Line} and {@link InvoiceReceiptLayout.Row}
 * through commons-beanutils. Under the module launch ({@code javafx:run}) that reflection needs the
 * package exported - without it every 80mm receipt fails with {@code IllegalAccessException}, measured
 * on 2026-09-15 - and needs nothing more, because it only calls public getters of public classes.
 * An unqualified {@code opens} had been added beside the export; it opened the package to deep
 * reflection from every module for no reader that needed it.
 */
class ReceiptBeansModuleExportTest {

    @Test
    void theReceiptPackageIsExportedAndNotOpened() throws Exception {
        String module = Files.readString(Path.of("src/main/java/module-info.java"));

        assertTrue(module.contains("exports com.hamza.account.features.invoice;"));
        assertFalse(module.contains("opens com.hamza.account.features.invoice;"),
                "public getters need an export; do not open the package to every module");
    }

    @Test
    void theBeansJasperReadsArePublicSoTheExportIsEnough() {
        for (Class<?> bean : new Class<?>[]{InvoiceReceiptLayout.Line.class, InvoiceReceiptLayout.Row.class}) {
            assertTrue(Modifier.isPublic(bean.getModifiers()), bean + " must stay public");
        }
    }
}
