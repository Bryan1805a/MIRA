package com.mira.app.ui;

import java.util.regex.Pattern;

/**
 * Converts the assistant's lightweight Markdown into readable plain text for
 * the chat bubble.
 *
 * <p>The backend stores and returns whatever the provider produced, and the
 * client renders plain {@link android.widget.TextView}s, so markers such as
 * {@code **bold**}, {@code ## Heading}, or fenced code blocks would otherwise
 * appear literally on screen.
 *
 * <p>The conversion is deliberately conservative and format-preserving: it
 * strips markers rather than dropping content, normalizes list bullets, keeps
 * line breaks, and converts links to {@code text (url)}. Emphasis markers are
 * only removed when they wrap non-empty text without inner padding, so
 * arithmetic such as {@code 2 * 3 = 6} or identifiers such as
 * {@code snake_case} survive unchanged.
 *
 * <p>Only {@code java.lang.String} and {@code java.util.regex} are used, so the
 * behaviour is unit-testable on a plain JVM.
 */
public final class MarkdownText {

    private static final Pattern HEADING =
            Pattern.compile("^\\s{0,3}#{1,6}\\s+");

    private static final Pattern BULLET =
            Pattern.compile("^(\\s*)[-*+]\\s+");

    private static final Pattern HORIZONTAL_RULE =
            Pattern.compile("^\\s*([-*_])(\\s*\\1){2,}\\s*$");

    private static final Pattern FENCE =
            Pattern.compile("^\\s*```.*$");

    private static final Pattern LINK =
            Pattern.compile("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)");

    /**
     * Display-math fences only. Single-dollar amounts are left untouched so
     * prices such as {@code $5} are never swallowed.
     */
    private static final Pattern MATH_BLOCK =
            Pattern.compile("\\$\\$(.+?)\\$\\$", Pattern.DOTALL);

    private static final Pattern BOLD =
            Pattern.compile("\\*\\*(?!\\s)(.+?)(?<!\\s)\\*\\*");

    private static final Pattern BOLD_UNDERSCORE =
            Pattern.compile("__(?!\\s)(.+?)(?<!\\s)__");

    private static final Pattern ITALIC =
            Pattern.compile("(?<![\\w*])\\*(?!\\s)([^*\\n]+?)(?<!\\s)\\*(?![\\w*])");

    private static final Pattern ITALIC_UNDERSCORE =
            Pattern.compile("(?<![\\w_])_(?!\\s)([^_\\n]+?)(?<!\\s)_(?![\\w_])");

    private static final Pattern INLINE_CODE =
            Pattern.compile("`([^`\\n]+?)`");

    private static final Pattern TRAILING_SPACES =
            Pattern.compile("(?m)[ \\t]+$");

    private static final Pattern EXTRA_BLANK_LINES =
            Pattern.compile("\\n{3,}");

    private MarkdownText() {
    }

    /**
     * @param source raw assistant content, may be {@code null}
     * @return text suitable for direct display; never {@code null}
     */
    public static String toPlainText(String source) {
        if (source == null) {
            return "";
        }

        if (source.isEmpty()) {
            return source;
        }

        String text = normalizeLines(source);

        text = MATH_BLOCK.matcher(text).replaceAll("$1");
        text = LINK.matcher(text).replaceAll("$1 ($2)");
        text = BOLD.matcher(text).replaceAll("$1");
        text = BOLD_UNDERSCORE.matcher(text).replaceAll("$1");
        text = ITALIC.matcher(text).replaceAll("$1");
        text = ITALIC_UNDERSCORE.matcher(text).replaceAll("$1");
        text = INLINE_CODE.matcher(text).replaceAll("$1");
        text = TRAILING_SPACES.matcher(text).replaceAll("");
        text = EXTRA_BLANK_LINES.matcher(text).replaceAll("\n\n");

        return text.trim();
    }

    private static String normalizeLines(String source) {
        String[] lines = source.split("\n", -1);
        StringBuilder builder = new StringBuilder(source.length());

        for (String line : lines) {
            String trimmed = line.trim();

            if (FENCE.matcher(trimmed).matches()) {
                continue;
            }

            if (HORIZONTAL_RULE.matcher(trimmed).matches()) {
                continue;
            }

            String cleaned = HEADING.matcher(line).replaceFirst("");
            cleaned = BULLET.matcher(cleaned).replaceFirst("$1\u2022 ");

            builder.append(cleaned).append('\n');
        }

        return builder.toString();
    }
}
