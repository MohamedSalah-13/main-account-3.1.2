package com.hamza.account.features.audit;

import java.time.LocalDateTime;

public record AuditRetentionPreview(LocalDateTime cutoff, long matchingRows) {
}
