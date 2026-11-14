package com.mira.app.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MarkdownTextTest {

    @Test
    public void stripsBoldAndItalicMarkers() {
        assertEquals(
                "Quantum computing is a type of computing.",
                MarkdownText.toPlainText(
                        "**Quantum computing** is a type of *computing*."
                )
        );

        assertEquals(
                "bits and qubits",
                MarkdownText.toPlainText("__bits__ and _qubits_")
        );
    }

    @Test
    public void stripsHeadingMarkersButKeepsText() {
        assertEquals(
                "The core idea: qubits",
                MarkdownText.toPlainText("## The core idea: qubits")
        );
        assertEquals(
                "Title",
                MarkdownText.toPlainText("# Title")
        );
    }

    @Test
    public void normalizesListBullets() {
        assertEquals(
                "\u2022 first\n\u2022 second",
                MarkdownText.toPlainText("- first\n* second")
        );
    }

    @Test
    public void removesCodeFencesAndHorizontalRules() {
        assertEquals(
                "int x = 1;",
                MarkdownText.toPlainText("```java\nint x = 1;\n```")
        );

        assertEquals(
                "before\nafter",
                MarkdownText.toPlainText("before\n---\nafter")
        );
    }

    @Test
    public void stripsInlineCodeBackticks() {
        assertEquals(
                "call the read method",
                MarkdownText.toPlainText("call the `read` method")
        );
    }

    @Test
    public void convertsLinksToReadableForm() {
        assertEquals(
                "see the docs (https://example.com/a)",
                MarkdownText.toPlainText(
                        "see the [docs](https://example.com/a)"
                )
        );
    }

    @Test
    public void preservesArithmeticAndIdentifiers() {
        assertEquals(
                "2 * 3 = 6",
                MarkdownText.toPlainText("2 * 3 = 6")
        );

        assertEquals(
                "use snake_case here",
                MarkdownText.toPlainText("use snake_case here")
        );
    }

    @Test
    public void stripsDisplayMathFences() {
        assertEquals(
                "E = mc^2",
                MarkdownText.toPlainText("$$ E = mc^2 $$")
        );

        assertEquals(
                "the state is (a+b)",
                MarkdownText.toPlainText("the state is $$(a+b)$$")
        );
    }

    @Test
    public void leavesCurrencyAmountsAlone() {
        assertEquals(
                "it costs $5 today",
                MarkdownText.toPlainText("it costs $5 today")
        );
    }

    @Test
    public void preservesPlainTextUnchanged() {
        String plain = "A qubit can be 0 and 1 at the same time.";

        assertEquals(plain, MarkdownText.toPlainText(plain));
    }

    @Test
    public void handlesNullAndEmpty() {
        assertEquals("", MarkdownText.toPlainText(null));
        assertEquals("", MarkdownText.toPlainText(""));
    }

    @Test
    public void collapsesExcessBlankLines() {
        assertEquals(
                "one\n\ntwo",
                MarkdownText.toPlainText("one\n\n\n\ntwo")
        );
    }
}
