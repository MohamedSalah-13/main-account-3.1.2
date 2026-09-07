package com.hamza.account.features.audit;

import java.io.IOException;
import java.nio.file.Path;

public interface AuditLogExporter {
    AuditExportFormat format();
    void export(Path target, AuditExportDocument document) throws IOException;
}
