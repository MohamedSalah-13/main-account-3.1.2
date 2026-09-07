package com.hamza.account.features.audit;

import java.nio.file.Path;

public record AuditExportResult(Path file, int rowCount) {
}
