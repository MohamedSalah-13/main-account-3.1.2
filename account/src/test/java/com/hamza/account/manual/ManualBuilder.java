package com.hamza.account.manual;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Builds the user manual PDF out of {@code docs/manual}.
 * <p>
 * Takes no database and opens no window: it is the half of the job that turns prose and already
 * captured pictures into the file a customer is handed. {@code ManualCaptureApplication} is the
 * other half and runs on its own.
 */
public final class ManualBuilder {

    private ManualBuilder() {
    }

    /**
     * @param root    the repository root
     * @param version what the cover and every footer say this manual describes
     * @param target  where the PDF is written
     * @return what was built, for a caller that wants to report it
     */
    public static Result build(Path root, String version, Path target) throws IOException {
        List<ManualPage> pages = ManualSource.load(root);
        if (pages.isEmpty()) {
            throw new IOException("No manual pages found under " + ManualSource.folder(root).toAbsolutePath());
        }
        Path images = ManualSource.images(root);
        ManualMeta meta = new ManualMeta("AccountK", version, LocalDate.now(), images);
        Map<String, Integer> numbers = new ManualPdfWriter().write(pages, meta, target);

        long figures = pages.stream().flatMap(page -> page.blocks().stream())
                .filter(block -> block instanceof ManualBlock.Figure).count();
        long missing = pages.stream().flatMap(page -> page.blocks().stream())
                .filter(block -> block instanceof ManualBlock.Figure)
                .map(block -> ((ManualBlock.Figure) block).imageId())
                .filter(id -> !Files.isRegularFile(images.resolve(id + ".png")))
                .distinct().count();
        return new Result(target, pages.size(), numbers.size(), figures, missing);
    }

    /**
     * @param pdf            what was written
     * @param pages          how many manual pages went into it
     * @param entries        how many contents entries it carries
     * @param figures        how many pictures it places
     * @param missingPictures how many distinct pictures were not on disk and printed as a placeholder
     */
    public record Result(Path pdf, int pages, int entries, long figures, long missingPictures) {
    }

    /** {@code java ... ManualBuilder <repository root> <version> <target pdf>} */
    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : ".");
        String version = args.length > 1 ? args[1] : "dev";
        Path target = Path.of(args.length > 2 ? args[2] : "docs/manual/AccountK-User-Manual.pdf");
        Result result = build(root, version, target);
        System.out.println("manual: " + result.pdf().toAbsolutePath());
        System.out.println("pages=" + result.pages() + " figures=" + result.figures()
                + " missing pictures=" + result.missingPictures());
    }
}
