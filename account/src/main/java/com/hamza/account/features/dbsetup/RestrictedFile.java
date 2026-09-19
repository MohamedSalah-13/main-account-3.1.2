package com.hamza.account.features.dbsetup;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Writes a file only Administrators and SYSTEM can read.
 * <p>
 * {@code %ProgramData%} is readable by every user of the machine by inheritance, and the root
 * password is kept there for the technician who adds a till later. The order is what matters:
 * the file is created empty and opened, its access list is replaced - which also drops the
 * inherited entries - and only then is the secret written through the handle already held. The
 * secret is never on disk under the folder's own permissions, not even for a moment, and the
 * write still succeeds for a caller the new list no longer admits.
 * <p>
 * The two groups are named by SID through {@code icacls}, not by name through
 * {@code UserPrincipalLookupService}: {@code BUILTIN\Administrators} is a localized string, and
 * this ships to machines whose Windows is not in English. Nothing secret is in those arguments.
 */
public final class RestrictedFile {

    static final String ADMINISTRATORS = "*S-1-5-32-544";
    static final String SYSTEM = "*S-1-5-18";

    private RestrictedFile() {
    }

    public static void write(Path file, String content) throws IOException, InterruptedException {
        Files.createDirectories(file.getParent());
        Files.deleteIfExists(file);
        try (FileChannel channel = FileChannel.open(file,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            restrict(file);
            channel.write(ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)));
            channel.force(true);
        }
    }

    private static void restrict(Path file) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(List.of("icacls", file.toString(),
                "/inheritance:r", "/grant:r", ADMINISTRATORS + ":F", SYSTEM + ":F"))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("icacls did not finish on " + file);
        }
        if (process.exitValue() != 0) {
            // A secret in a file anybody can read is worse than no file: the caller fails.
            throw new IOException("icacls could not restrict " + file + ", exit " + process.exitValue());
        }
    }
}
