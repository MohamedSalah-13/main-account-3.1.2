package com.hamza.account.service.version;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SystemInfo {

    private final String clientCode;
    private final String clientName;
    private final String appVersion;
    private final String databaseVersion;
    private final LocalDateTime installDate;
    private final LocalDateTime lastUpdate;
    private final String databaseName;
    private final String serverIp;
    private final String licenseKey;
    private final String notes;
}
