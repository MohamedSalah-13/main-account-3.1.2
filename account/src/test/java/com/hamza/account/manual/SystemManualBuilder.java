package com.hamza.account.manual;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Builds one subsystem guide without adding its pages to the complete program manual. */
public final class SystemManualBuilder {

    private SystemManualBuilder() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: SystemManualBuilder <source-dir> <title> <version> <target.pdf>");
        }
        Path source = Path.of(args[0]);
        Path images = source.resolve("images");
        Path target = Path.of(new String(java.util.Base64.getDecoder().decode(args[3]), StandardCharsets.UTF_8));
        List<ManualPage> pages;
        try (Stream<Path> paths = Files.walk(source)) {
            pages = paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".md"))
                    .sorted(Comparator.comparing(path -> source.relativize(path).toString()))
                    .map(path -> {
                        try {
                            return ManualParser.parse(source.relativize(path).toString(),
                                    Files.readString(path));
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    }).toList();
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
        if (pages.isEmpty()) {
            throw new IOException("No guide pages found under " + source.toAbsolutePath());
        }
        long figures = pages.stream().flatMap(page -> page.blocks().stream())
                .filter(block -> block instanceof ManualBlock.Figure).count();
        long missing = pages.stream().flatMap(page -> page.blocks().stream())
                .filter(block -> block instanceof ManualBlock.Figure)
                .map(block -> ((ManualBlock.Figure) block).imageId())
                .filter(id -> !Files.isRegularFile(images.resolve(id + ".png"))).distinct().count();
        if (missing > 0) {
            throw new IOException("Guide has " + missing + " missing illustration(s) under " + images);
        }
        String title = new String(java.util.Base64.getDecoder().decode(args[1]), StandardCharsets.UTF_8);
        new ManualPdfWriter().write(pages,
                new ManualMeta(title, args[2], LocalDate.now(), images), target);
        System.out.printf("guide: %s%npages=%d figures=%d%n", target.toAbsolutePath(), pages.size(), figures);
    }
}
