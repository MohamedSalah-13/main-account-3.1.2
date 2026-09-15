package com.hamza.account.features.users;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportRecoverySigningLogTest {

    private static final SupportRecoveryChallenge REQUEST =
            new SupportRecoveryChallenge("A1B2C3D4E5F60718", "PC-01", LocalDateTime.of(2026, 9, 7, 14, 5, 33));

    @TempDir
    Path folder;

    @Test
    void everySignatureIsOneLineUnderOneHeaderInAFolderMadeForIt() throws Exception {
        SupportRecoverySigningLog log = new SupportRecoverySigningLog(folder.resolve("support").resolve("signed.csv"));

        log.append(LocalDateTime.of(2026, 9, 7, 14, 9, 0), "سوبر ماركت النور", REQUEST);
        log.append(LocalDateTime.of(2026, 9, 8, 10, 0, 0), "Second shop", REQUEST);

        List<String> lines = Files.readAllLines(log.file(), StandardCharsets.UTF_8);
        assertEquals(3, lines.size());
        assertEquals('﻿' + SupportRecoverySigningLog.HEADER, lines.get(0));
        assertEquals("\"2026-09-07 14:09:00\",\"سوبر ماركت النور\",\"PC-01\",\"A1B2C3D4E5F60718\",\"2026-09-07 14:05:33\"",
                lines.get(1));
        assertTrue(lines.get(2).startsWith("\"2026-09-08 10:00:00\",\"Second shop\""));
    }

    /** A customer name is typed by a person and the file is opened in a spreadsheet. */
    @Test
    void whatAPersonTypedCannotBreakTheRowOrRunAsAFormula() {
        assertEquals("\"say \"\"hi\"\"\"", SupportRecoverySigningLog.csv("say \"hi\""));
        assertEquals("\"line one line two\"", SupportRecoverySigningLog.csv("line one\r\nline two"));
        assertEquals("\"'=HYPERLINK(1)\"", SupportRecoverySigningLog.csv("=HYPERLINK(1)"));
        assertEquals("\"'-1\"", SupportRecoverySigningLog.csv("-1"));
        assertEquals("\"\"", SupportRecoverySigningLog.csv(null));
    }
}
