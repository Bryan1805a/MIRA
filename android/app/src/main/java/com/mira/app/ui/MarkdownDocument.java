package com.mira.app.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits assistant Markdown into the small set of blocks MIRA renders: headings,
 * paragraphs, bullets and fenced code. Kept free of Android types so the parsing
 * rules can be unit tested on a plain JVM.
 */
public final class MarkdownDocument {

    public enum Kind {
        HEADING,
        PARAGRAPH,
        BULLET,
        CODE,
        BLANK
    }

    public static final class Block {

        private final Kind kind;
        private final int level;
        private final String text;

        private Block(Kind kind, int level, String text) {
            this.kind = kind;
            this.level = level;
            this.text = text;
        }

        public Kind kind() {
            return kind;
        }

        public int level() {
            return level;
        }

        public String text() {
            return text;
        }

        static Block heading(int level, String text) {
            return new Block(Kind.HEADING, level, text);
        }

        static Block paragraph(String text) {
            return new Block(Kind.PARAGRAPH, 0, text);
        }

        static Block bullet(String text) {
            return new Block(Kind.BULLET, 0, text);
        }

        static Block code(String text) {
            return new Block(Kind.CODE, 0, text);
        }

        static Block blank() {
            return new Block(Kind.BLANK, 0, "");
        }
    }

    private MarkdownDocument() {
    }

    public static List<Block> parse(String source) {
        List<Block> blocks = new ArrayList<>();

        if (source == null || source.isBlank()) {
            return blocks;
        }

        StringBuilder code = null;

        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();

            if (code != null) {
                if (trimmed.startsWith("```")) {
                    blocks.add(Block.code(trimTrailing(code.toString())));
                    code = null;
                } else {
                    code.append(line).append('\n');
                }
                continue;
            }

            if (trimmed.startsWith("```")) {
                code = new StringBuilder();
                continue;
            }

            if (trimmed.isEmpty()) {
                if (!blocks.isEmpty()
                        && blocks.get(blocks.size() - 1).kind() != Kind.BLANK) {
                    blocks.add(Block.blank());
                }
                continue;
            }

            if (isHorizontalRule(trimmed)) {
                continue;
            }

            Block heading = heading(trimmed);

            if (heading != null) {
                blocks.add(heading);
                continue;
            }

            if (isBullet(trimmed)) {
                blocks.add(Block.bullet(trimmed.substring(2).trim()));
                continue;
            }

            blocks.add(Block.paragraph(trimmed));
        }

        if (code != null) {
            blocks.add(Block.code(trimTrailing(code.toString())));
        }

        return blocks;
    }

    private static Block heading(String trimmed) {
        int level = 0;

        while (level < trimmed.length() && trimmed.charAt(level) == '#') {
            level++;
        }

        if (level < 1 || level > 6 || level >= trimmed.length()) {
            return null;
        }

        if (!Character.isWhitespace(trimmed.charAt(level))) {
            return null;
        }

        String text = trimmed.substring(level).trim();
        text = text.replaceAll("#+\\s*$", "").trim();

        return text.isEmpty() ? null : Block.heading(level, text);
    }

    private static boolean isBullet(String trimmed) {
        if (trimmed.length() < 3) {
            return false;
        }

        char marker = trimmed.charAt(0);

        return (marker == '-' || marker == '*' || marker == '+')
                && Character.isWhitespace(trimmed.charAt(1));
    }

    private static boolean isHorizontalRule(String trimmed) {
        if (trimmed.length() < 3) {
            return false;
        }

        char first = trimmed.charAt(0);

        if (first != '-' && first != '*' && first != '_') {
            return false;
        }

        for (int index = 0; index < trimmed.length(); index++) {
            char current = trimmed.charAt(index);

            if (current != first && !Character.isWhitespace(current)) {
                return false;
            }
        }

        return true;
    }

    private static String trimTrailing(String value) {
        return value.replaceAll("\\s+$", "");
    }
}
