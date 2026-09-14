package com.hamza.account.features.backup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Whether a {@code mysqldump} output is the whole of one.
 *
 * <p>{@code mysqldump} ends its output with a {@code -- Dump completed} comment, written after
 * the last table and only then. So a dump whose final line is that comment was not cut short -
 * by a disk that filled, a connection that dropped, a process that was killed - and a dump
 * whose final line is anything else was. Nothing checked it: the backup service trusted the
 * exit code, and the restore looked at the first 64 KB for something that resembled SQL, which a
 * file truncated halfway through its data answers perfectly well. The restore then dropped every
 * table and stopped wherever the file did.
 *
 * <p>It is an {@link OutputStream} so it can be fed while a backup is decrypted, without the
 * plaintext ever touching the disk a second time. Only line starts are inspected, and only for
 * ASCII, so the bytes are read as they come rather than decoded.
 */
public final class DumpCheck extends OutputStream {

    static final String COMPLETED = "-- Dump completed";
    private static final byte[] CREATE_TABLE = "CREATE TABLE ".getBytes(StandardCharsets.US_ASCII);
    private static final int KEPT = 256;

    private final byte[] line = new byte[KEPT];
    private int length;
    private boolean inLine;
    private String lastLine = "";
    private int tables;

    /** Reads {@code in} to its end and answers for it. */
    public static DumpCheck of(InputStream in) throws IOException {
        DumpCheck check = new DumpCheck();
        in.transferTo(check);
        check.close();
        return check;
    }

    @Override
    public void write(int b) {
        if (b == '\n') {
            endLine();
            return;
        }
        inLine = true;
        if (length < KEPT) {
            line[length++] = (byte) b;
        }
    }

    @Override
    public void write(byte[] bytes, int offset, int count) {
        for (int i = offset; i < offset + count; i++) {
            write(bytes[i]);
        }
    }

    /** Takes the last line when the output did not end with a newline. */
    @Override
    public void close() {
        if (inLine) {
            endLine();
        }
    }

    private void endLine() {
        int end = length;
        while (end > 0 && (line[end - 1] == '\r' || line[end - 1] == ' ' || line[end - 1] == '\t')) {
            end--;
        }
        if (end > 0) {
            lastLine = new String(line, 0, end, StandardCharsets.US_ASCII);
            if (startsWith(CREATE_TABLE, end)) {
                tables++;
            }
        }
        length = 0;
        inLine = false;
    }

    private boolean startsWith(byte[] prefix, int end) {
        if (end < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (line[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** The dump reached its closing comment: nothing after the last table was lost. */
    public boolean completed() {
        return lastLine.startsWith(COMPLETED);
    }

    /** How many tables it creates. A dump of an existing schema creates at least one. */
    public int tables() {
        return tables;
    }

    /** Complete, and a dump of something. */
    public boolean usable() {
        return completed() && tables > 0;
    }
}
