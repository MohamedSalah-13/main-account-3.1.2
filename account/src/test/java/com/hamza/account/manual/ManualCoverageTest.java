package com.hamza.account.manual;

import com.hamza.account.features.shortcuts.SidebarShortcut;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What keeps the manual describing the program rather than the program it used to be.
 * <p>
 * The sidebar's own {@link SidebarShortcut} is the list of screens, so a screen added to the
 * program is a screen this test notices is undocumented. {@link #UNDOCUMENTED} is the debt, written
 * down and <b>failing in both directions</b>: a screen documented while still listed here fails,
 * and so does one that is neither documented nor listed. It can only ever shrink on purpose.
 */
class ManualCoverageTest {

    /** Surefire's working directory is the module, so the repository root is one up. */
    private static final Path ROOT = Path.of("..");

    /** Commands that are not screens and will never have a page of their own. */
    private static final Set<SidebarShortcut> NOT_A_SCREEN = EnumSet.of(
            SidebarShortcut.CLOSE,      // closes the program
            SidebarShortcut.YOUTUBE);   // opens a video in the browser

    /**
     * Screens the manual does not cover yet - empty, and meant to stay that way. A screen added to
     * the sidebar fails {@link #theDebtIsHonest()} until it has a page here or is listed below, so
     * the only way to ship a screen nobody can look up is to write down that you are doing it.
     */
    private static final Set<SidebarShortcut> UNDOCUMENTED = EnumSet.noneOf(SidebarShortcut.class);

    /** Arabic letters. A code span is for text that reads left to right, and only for that. */
    private static final Pattern ARABIC = Pattern.compile("[\\u0600-\\u06FF]");
    private static final Pattern CODE_SPAN = Pattern.compile("`([^`]+)`");

    private static List<ManualPage> pages() throws IOException {
        return ManualSource.load(ROOT);
    }

    private static Set<SidebarShortcut> documented() throws IOException {
        Set<SidebarShortcut> documented = EnumSet.noneOf(SidebarShortcut.class);
        for (ManualPage page : pages()) {
            page.shortcut().ifPresent(name -> documented.add(SidebarShortcut.valueOf(name)));
        }
        return documented;
    }

    @Test
    @DisplayName("every page parses and its header is complete")
    void everyPageParses() throws IOException {
        assertTrue(pages().size() > 0, "the manual has no pages at all");
    }

    @Test
    @DisplayName("a page's shortcut names a real sidebar command")
    void shortcutsAreReal() throws IOException {
        for (ManualPage page : pages()) {
            page.shortcut().ifPresent(name -> assertTrue(
                    EnumSet.allOf(SidebarShortcut.class).stream().anyMatch(s -> s.name().equals(name)),
                    "Manual page " + page.id() + " names a shortcut that does not exist: " + name));
        }
    }

    @Test
    @DisplayName("the undocumented list is exactly the screens with no page - in both directions")
    void theDebtIsHonest() throws IOException {
        Set<SidebarShortcut> documented = documented();
        Set<SidebarShortcut> missing = EnumSet.allOf(SidebarShortcut.class);
        missing.removeAll(documented);
        missing.removeAll(NOT_A_SCREEN);

        Set<String> undocumentedButWritten = new TreeSet<>();
        for (SidebarShortcut listed : UNDOCUMENTED) {
            if (documented.contains(listed)) {
                undocumentedButWritten.add(listed.name());
            }
        }
        Set<String> writtenNowhere = missing.stream()
                .filter(shortcut -> !UNDOCUMENTED.contains(shortcut))
                .map(SidebarShortcut::name)
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(Set.of(), undocumentedButWritten,
                "these screens now have a manual page - take them out of UNDOCUMENTED");
        assertEquals(Set.of(), writtenNowhere,
                "these screens have no manual page and are not listed as missing one");
    }

    @Test
    @DisplayName("no two pages claim the same id or the same picture")
    void nothingIsClaimedTwice() throws IOException {
        List<String> ids = new ArrayList<>();
        List<String> pictures = new ArrayList<>();
        for (ManualPage page : pages()) {
            assertTrue(ids.add(page.id()));
            page.screenshot().ifPresent(pictures::add);
        }
        assertEquals(ids.size(), Set.copyOf(ids).size(), "two manual pages share an id");
        assertEquals(pictures.size(), Set.copyOf(pictures).size(), "two manual pages share a picture");
    }

    @Test
    @DisplayName("a page naming a picture places it, and a placed picture is named in the header")
    void picturesAreDeclaredAndUsed() throws IOException {
        for (ManualPage page : pages()) {
            if (page.screenshot().isPresent()) {
                assertTrue(page.showsItsScreenshot(),
                        "Manual page " + page.id() + " names a screenshot its prose never places, "
                                + "so the harness takes a picture nobody sees");
            }
        }
    }

    @Test
    @DisplayName("the source files a page is measured against exist")
    void sourcesExist() throws IOException {
        for (ManualPage page : pages()) {
            for (String source : page.sources()) {
                assertTrue(Files.exists(ROOT.resolve(source)),
                        "Manual page " + page.id() + " names a source that is not there: " + source
                                + " - a renamed controller leaves the page measuring nothing");
            }
        }
    }

    @Test
    @DisplayName("a code span holds no Arabic - that is what **bold** is for")
    void codeSpansAreLeftToRight() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : markdownFiles()) {
            var matcher = CODE_SPAN.matcher(Files.readString(file));
            while (matcher.find()) {
                if (ARABIC.matcher(matcher.group(1)).find()) {
                    offenders.add(file.getFileName() + ": `" + matcher.group(1) + "`");
                }
            }
        }
        assertEquals(List.of(), offenders,
                "a code span is wrapped in a left-to-right isolate before shaping; Arabic inside "
                        + "one is text told to read the wrong way. Use **bold** for a screen's own "
                        + "captions and a code span for keys, file names and versions.");
    }

    private static List<Path> markdownFiles() throws IOException {
        try (var files = Files.walk(ManualSource.folder(ROOT))) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".md"))
                    .toList();
        }
    }
}
