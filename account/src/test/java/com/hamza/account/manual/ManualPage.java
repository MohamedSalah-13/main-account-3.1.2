package com.hamza.account.manual;

import java.util.List;
import java.util.Optional;

/**
 * One page of the manual, as its {@code .md} file declares it.
 * <p>
 * The header is the whole registry: there is no second list of screens in Java to keep in step
 * with the prose. {@link ManualCoverageTest} reads these files and fails the build when a
 * {@code SidebarShortcut} the sidebar offers has no page here, so a screen added to the program
 * cannot quietly stay out of the manual.
 *
 * @param id         the file's own name, and what a cross-reference uses
 * @param title      the Arabic heading the page carries and the contents list prints
 * @param chapter    which part of the manual it belongs to
 * @param shortcut   the {@code SidebarShortcut} constant this page documents, where it is a screen
 * @param screenshot the picture id the capture harness writes, where the page shows one
 * @param sources    the controller and FXML that define the screen - what a stale picture is
 *                   measured against
 * @param blocks     the prose
 */
public record ManualPage(String id,
                         String title,
                         String chapter,
                         Optional<String> shortcut,
                         Optional<String> screenshot,
                         List<String> sources,
                         List<ManualBlock> blocks) {

    public ManualPage {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A manual page needs an id");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("A manual page needs a title: " + id);
        }
        if (chapter == null || chapter.isBlank()) {
            throw new IllegalArgumentException("A manual page needs a chapter: " + id);
        }
        sources = List.copyOf(sources);
        blocks = List.copyOf(blocks);
    }

    /** A page that names a screenshot but whose prose never places one is a picture nobody sees. */
    public boolean showsItsScreenshot() {
        return blocks.stream().anyMatch(block -> block instanceof ManualBlock.Figure);
    }
}
