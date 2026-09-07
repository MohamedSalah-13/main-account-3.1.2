package com.hamza.account.features.audit;

import java.io.IOException;
import java.nio.file.Path;

public interface AuditAdminExporter {
    AuditExportFormat format();
    void export(Path target, AuditAdminExportDocument document) throws IOException;
}
