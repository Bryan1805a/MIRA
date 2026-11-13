package com.mira.app.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MemoryOptionsTest {

    @Test
    public void unspecifiedValuesMapToTheFirstOption() {
        assertEquals(0, MemoryOptions.indexOf(
                MemoryOptions.FIELD_DEPTH,
                null
        ));
        assertEquals(0, MemoryOptions.indexOf(
                MemoryOptions.FIELD_DEPTH,
                ""
        ));
        assertEquals(0, MemoryOptions.indexOf(
                MemoryOptions.FIELD_EXAMPLES,
                "no_strong_preference"
        ));
    }

    @Test
    public void knownValuesMapToTheirOption() {
        assertEquals(2, MemoryOptions.indexOf(
                MemoryOptions.FIELD_DEPTH,
                "balanced"
        ));
        assertEquals(1, MemoryOptions.indexOf(
                MemoryOptions.FIELD_TONE,
                "conversational"
        ));
        assertEquals(3, MemoryOptions.indexOf(
                MemoryOptions.FIELD_FORMATTING,
                "bulleted_lists"
        ));
    }

    @Test
    public void unknownValuesAreRejected() {
        assertEquals(-1, MemoryOptions.indexOf(
                MemoryOptions.FIELD_TONE,
                "sarcastic"
        ));
    }
}