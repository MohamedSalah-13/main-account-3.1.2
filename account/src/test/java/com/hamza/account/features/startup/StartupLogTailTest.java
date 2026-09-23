package com.hamza.account.features.startup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupLogTailTest {

    @TempDir
    Path folder;

    private static void append(Path file, String text) throws Exception {
        Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Test
    @DisplayName("it starts where the file ended, so an earlier run's lines are not this one's")
    void startsAtTheEnd() throws Exception {
        Path file = folder.resolve("app.log");
        append(file, "[INFO ] yesterday's start\n");
        StartupLogTail tail = StartupLogTail.from(file);
        append(file, "[INFO ] Flyway applied 2 migration(s)\n");

        assertTrue(tail.poll());
        assertEquals(List.of("[INFO ] Flyway applied 2 migration(s)"), tail.lines());
        assertFalse(tail.poll(), "nothing new written, nothing read");
    }

    @Test
    @DisplayName("a stack frame is left out; the exception's line and its cause stay")
    void stackFramesAreLeftOut() throws Exception {
        Path file = folder.resolve("app.log");
        StartupLogTail tail = StartupLogTail.from(file);
        append(file, """
                [ERROR] Database migration failed
                java.lang.RuntimeException: Communications link failure
                \tat com.mysql.cj.jdbc.ConnectionImpl.connect(ConnectionImpl.java:1)
                \tat java.base/java.lang.Thread.run(Thread.java:1583)
                Caused by: java.net.ConnectException: Connection refused
                \t... 12 more
                """);

        tail.poll();

        assertEquals(List.of("[ERROR] Database migration failed",
                "java.lang.RuntimeException: Communications link failure",
                "Caused by: java.net.ConnectException: Connection refused"), tail.lines());
    }

    @Test
    @DisplayName("a line still being written waits for its end")
    void aPartialLineWaits() throws Exception {
        Path file = folder.resolve("app.log");
        StartupLogTail tail = StartupLogTail.from(file);
        append(file, "[INFO ] Applying V8");
        tail.poll();
        assertTrue(tail.lines().isEmpty());

        append(file, "1\n");
        tail.poll();
        assertEquals(List.of("[INFO ] Applying V81"), tail.lines());
    }

    @Test
    @DisplayName("a file created after the window opened is read from its start")
    void aNewFile() throws Exception {
        Path file = folder.resolve("logs").resolve("app.log");
        StartupLogTail tail = StartupLogTail.from(file);
        assertFalse(tail.poll());

        Files.createDirectories(file.getParent());
        append(file, "[INFO ] first line\n");

        assertTrue(tail.poll());
        assertEquals(List.of("[INFO ] first line"), tail.lines());
    }

    @Test
    @DisplayName("a file rolled over and started again is read from its new start")
    void aRolledFile() throws Exception {
        Path file = folder.resolve("app.log");
        append(file, "x".repeat(200) + "\n");
        StartupLogTail tail = StartupLogTail.from(file);
        Files.writeString(file, "[INFO ] after the roll\n", StandardCharsets.UTF_8);

        tail.poll();

        assertEquals(List.of("[INFO ] after the roll"), tail.lines());
    }

    @Test
    @DisplayName("only the newest lines are kept")
    void boundedLines() throws Exception {
        Path file = folder.resolve("app.log");
        StartupLogTail tail = StartupLogTail.from(file);
        StringBuilder text = new StringBuilder();
        for (int line = 1; line <= StartupLogTail.MAX_LINES + 5; line++) {
            text.append("line ").append(line).append('\n');
        }
        append(file, text.toString());

        tail.poll();

        assertEquals(StartupLogTail.MAX_LINES, tail.lines().size());
        assertEquals("line 6", tail.lines().getFirst());
    }
}
