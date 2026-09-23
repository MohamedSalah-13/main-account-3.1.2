package com.hamza.account.features.startup;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The technical side of the startup window: what this start wrote to the program's own log file.
 *
 * <p>It reads the file rather than attaching to the logging library, and that is deliberate: the file is
 * the record support will ask for anyway, and a panel showing it shows exactly what they will read. It
 * starts where the file ended when the window opened, so an earlier run's lines are not this one's.</p>
 *
 * <p>A stack frame is left out ({@code \tat ...}, {@code \t... 12 more}); the exception's own line and
 * its causes stay. The frames are for whoever opens the file, and fifty lines of them bury the one
 * sentence the panel is opened to find. Only the newest {@link #MAX_LINES} are kept.</p>
 */
public final class StartupLogTail {

    public static final int MAX_LINES = 400;

    private final Path file;
    private final Deque<String> lines = new ArrayDeque<>();
    private long offset;
    private String partial = "";

    private StartupLogTail(Path file, long offset) {
        this.file = file;
        this.offset = offset;
    }

    /** Starts at the file's current end - or at its start, once it is created, if it does not exist yet. */
    public static StartupLogTail from(Path file) {
        long end;
        try {
            end = Files.isRegularFile(file) ? Files.size(file) : 0;
        } catch (IOException e) {
            end = 0;
        }
        return new StartupLogTail(file, end);
    }

    /** Reads what was written since the last call, and answers whether anything was. */
    public synchronized boolean poll() {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try (RandomAccessFile reader = new RandomAccessFile(file.toFile(), "r")) {
            long length = reader.length();
            if (length < offset) {
                // Rolled over to a new file: read the new one from its start.
                offset = 0;
                partial = "";
            }
            if (length == offset) {
                return false;
            }
            byte[] bytes = new byte[(int) Math.min(length - offset, 1 << 20)];
            reader.seek(offset);
            reader.readFully(bytes);
            offset += bytes.length;
            accept(new String(bytes, StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** The lines read so far, oldest first. */
    public synchronized List<String> lines() {
        return new ArrayList<>(lines);
    }

    public Path file() {
        return file;
    }

    void accept(String text) {
        String[] parts = (partial + text).split("\r?\n", -1);
        partial = parts[parts.length - 1];
        for (int index = 0; index < parts.length - 1; index++) {
            String line = parts[index];
            if (line.isBlank() || isStackFrame(line)) {
                continue;
            }
            lines.addLast(line);
            if (lines.size() > MAX_LINES) {
                lines.removeFirst();
            }
        }
    }

    static boolean isStackFrame(String line) {
        String trimmed = line.stripLeading();
        return (line.startsWith("\t") || line.startsWith("    "))
                && (trimmed.startsWith("at ") || trimmed.startsWith("..."));
    }
}
