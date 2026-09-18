package com.hamza.account.manual;

import java.util.List;

/** One piece of a manual page. The renderer switches on these and nothing else. */
public sealed interface ManualBlock {

    /** A section title inside a page. Level 2 is a section, level 3 a sub-section. */
    record Heading(int level, String text) implements ManualBlock {
    }

    /** A flowing paragraph. Wrapped and shaped a line at a time - see {@link ManualText}. */
    record Para(String text) implements ManualBlock {
    }

    /** An unordered list. */
    record Bullets(List<String> items) implements ManualBlock {
    }

    /** A numbered procedure. The numbers are drawn by the renderer, never typed in the source. */
    record Steps(List<String> items) implements ManualBlock {
    }

    /** A boxed aside. */
    record Callout(Kind kind, String text) implements ManualBlock {

        public enum Kind {
            /** ملاحظة - something worth knowing. */
            NOTE,
            /** تحذير - something that loses data or money if got wrong. */
            WARNING,
            /** نصيحة - a faster way to do the same thing. */
            TIP
        }
    }

    /**
     * A screenshot. {@code imageId} names the file the capture harness writes, without a suffix,
     * so the page never has to know where the images live or what the picture is called on disk.
     */
    record Figure(String imageId, String caption) implements ManualBlock {
    }

    /** The label/value rows above a screen's description: where it opens from, its shortcut. */
    record Facts(List<Fact> rows) implements ManualBlock {

        public record Fact(String label, String value) {
        }
    }
}
