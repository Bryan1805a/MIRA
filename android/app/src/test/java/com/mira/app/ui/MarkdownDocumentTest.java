package com.mira.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class MarkdownDocumentTest {

    @Test
    public void emptyInputProducesNoBlocks() {
        assertTrue(MarkdownDocument.parse("").isEmpty());
        assertTrue(MarkdownDocument.parse("   ").isEmpty());
        assertTrue(MarkdownDocument.parse(null).isEmpty());
    }

    @Test
    public void headingsKeepTheirLevelAndDropTrailingHashes() {
        List<MarkdownDocument.Block> blocks =
                MarkdownDocument.parse("## Overview ##");

        assertEquals(1, blocks.size());
        assertEquals(MarkdownDocument.Kind.HEADING, blocks.get(0).kind());
        assertEquals(2, blocks.get(0).level());
        assertEquals("Overview", blocks.get(0).text());
    }

    @Test
    public void paragraphsAndBulletsAreSeparated() {
        List<MarkdownDocument.Block> blocks = MarkdownDocument.parse(
                "Intro line\n- first\n- second\n\nAfter"
        );

        assertEquals(5, blocks.size());
        assertEquals(MarkdownDocument.Kind.PARAGRAPH, blocks.get(0).kind());
        assertEquals(MarkdownDocument.Kind.BULLET, blocks.get(1).kind());
        assertEquals("first", blocks.get(1).text());
        assertEquals("second", blocks.get(2).text());
        assertEquals(MarkdownDocument.Kind.BLANK, blocks.get(3).kind());
        assertEquals(MarkdownDocument.Kind.PARAGRAPH, blocks.get(4).kind());
    }

    @Test
    public void fencedCodeKeepsItsLines() {
        List<MarkdownDocument.Block> blocks = MarkdownDocument.parse(
                "Before\n```java\nint a = 1;\nint b = 2;\n```\nAfter"
        );

        assertEquals(3, blocks.size());
        assertEquals(MarkdownDocument.Kind.CODE, blocks.get(1).kind());
        assertEquals("int a = 1;\nint b = 2;", blocks.get(1).text());
        assertEquals(MarkdownDocument.Kind.PARAGRAPH, blocks.get(2).kind());
    }

    @Test
    public void unterminatedCodeFenceStillRenders() {
        List<MarkdownDocument.Block> blocks = MarkdownDocument.parse(
                "```\nprint(1)\n"
        );

        assertEquals(1, blocks.size());
        assertEquals(MarkdownDocument.Kind.CODE, blocks.get(0).kind());
        assertEquals("print(1)", blocks.get(0).text());
    }

    @Test
    public void boldLinesAreNotMistakenForBullets() {
        List<MarkdownDocument.Block> blocks =
                MarkdownDocument.parse("**Heads up**");

        assertEquals(1, blocks.size());
        assertEquals(MarkdownDocument.Kind.PARAGRAPH, blocks.get(0).kind());
        assertEquals("**Heads up**", blocks.get(0).text());
    }

    @Test
    public void horizontalRulesAreDropped() {
        List<MarkdownDocument.Block> blocks = MarkdownDocument.parse(
                "One\n---\nTwo"
        );

        assertEquals(2, blocks.size());
        assertEquals("One", blocks.get(0).text());
        assertEquals("Two", blocks.get(1).text());
    }

    @Test
    public void consecutiveBlankLinesCollapse() {
        List<MarkdownDocument.Block> blocks = MarkdownDocument.parse(
                "One\n\n\n\nTwo"
        );

        assertEquals(3, blocks.size());
        assertEquals(MarkdownDocument.Kind.BLANK, blocks.get(1).kind());
    }

    @Test
    public void hashesWithoutSpaceStayLiteral() {
        List<MarkdownDocument.Block> blocks =
                MarkdownDocument.parse("#notaheading");

        assertEquals(1, blocks.size());
        assertEquals(MarkdownDocument.Kind.PARAGRAPH, blocks.get(0).kind());
    }
}
