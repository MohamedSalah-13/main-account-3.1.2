package com.hamza.account.manual;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualParserTest {

    private static ManualPage parse(String body) {
        return ManualParser.parse("sample", """
                ---
                title: عنوان
                chapter: فصل
                ---
                """ + body);
    }

    @Test
    @DisplayName("a callout written over several lines is one box, and its kind is the first line's")
    void joinsAQuotedRun() {
        ManualPage page = parse("""
                > تحذير: السطر الأول
                > السطر الثاني
                > السطر الثالث
                """);

        assertEquals(1, page.blocks().size(),
                "each line was its own box once - a three-line warning printed as three boxes");
        ManualBlock.Callout callout = assertInstanceOf(ManualBlock.Callout.class, page.blocks().getFirst());
        assertEquals(ManualBlock.Callout.Kind.WARNING, callout.kind(),
                "the two continuation lines carry no prefix and must not each default to a note");
        assertEquals("السطر الأول السطر الثاني السطر الثالث", callout.text());
    }

    @Test
    @DisplayName("a step wrapped over two source lines stays one step, so the numbering runs on")
    void continuesAStepOntoTheNextLine() {
        ManualPage page = parse("""
                1. الخطوة الأولى
                2. الخطوة الثانية تمتد
                   على سطرين
                3. الخطوة الثالثة
                4. الخطوة الرابعة
                """);

        assertEquals(1, page.blocks().size(), "one procedure, not two");
        ManualBlock.Steps steps = assertInstanceOf(ManualBlock.Steps.class, page.blocks().getFirst());
        assertEquals(4, steps.items().size(), "the split restarted the numbering at 1");
        assertEquals("الخطوة الثانية تمتد على سطرين", steps.items().get(1));
    }

    @Test
    @DisplayName("a bullet wrapped over two source lines stays one bullet")
    void continuesABulletOntoTheNextLine() {
        ManualPage page = parse("""
                - الأول
                - الثاني يمتد
                  على سطرين
                """);

        ManualBlock.Bullets bullets = assertInstanceOf(ManualBlock.Bullets.class, page.blocks().getFirst());
        assertEquals(List.of("الأول", "الثاني يمتد على سطرين"), bullets.items());
    }

    @Test
    @DisplayName("a blank line ends a list, so the paragraph after it is a paragraph")
    void aBlankLineEndsAList() {
        ManualPage page = parse("""
                - الأول

                فقرة بعد القائمة
                """);

        assertEquals(2, page.blocks().size());
        assertInstanceOf(ManualBlock.Bullets.class, page.blocks().getFirst());
        assertInstanceOf(ManualBlock.Para.class, page.blocks().getLast());
    }

    @Test
    @DisplayName("a figure names a picture id, never a path")
    void readsAFigure() {
        ManualPage page = parse("![شرح الصورة](sales-invoice)\n");

        ManualBlock.Figure figure = assertInstanceOf(ManualBlock.Figure.class, page.blocks().getFirst());
        assertEquals("sales-invoice", figure.imageId());
        assertEquals("شرح الصورة", figure.caption());
    }

    @Test
    @DisplayName("consecutive | label | value | lines are one facts block")
    void readsFacts() {
        ManualPage page = parse("""
                | الكود | رقم الفاتورة |
                | الاسم | العميل |
                """);

        ManualBlock.Facts facts = assertInstanceOf(ManualBlock.Facts.class, page.blocks().getFirst());
        assertEquals(2, facts.rows().size());
        assertEquals("الكود", facts.rows().getFirst().label());
        assertEquals("العميل", facts.rows().getLast().value());
    }

    @Test
    @DisplayName("anything the grammar does not know is prose, so nothing is silently dropped")
    void unknownSyntaxIsProse() {
        ManualPage page = parse("*** ليس أي شيء معروف ***\n");

        assertInstanceOf(ManualBlock.Para.class, page.blocks().getFirst());
    }

    @Test
    @DisplayName("a page without a header, or missing a required key, is refused with its name")
    void refusesABrokenPage() {
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> ManualParser.parse("broken", "بلا ترويسة")).getMessage().contains("broken"));

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> ManualParser.parse("broken", "---\ntitle: عنوان\n---\n")).getMessage().contains("chapter"));
    }
}
