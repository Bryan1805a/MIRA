package com.mira.app.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import androidx.core.content.ContextCompat;

import com.mira.app.R;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders assistant Markdown as styled text: real headings, bold/italic spans,
 * inline code, fenced code blocks and hanging-indented bullets. The message stays
 * a single TextView, so list recycling and the typewriter reveal keep working.
 */
public final class RichText {

    private static final Pattern LINK =
            Pattern.compile("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)");

    private static final int BULLET_INDENT_DP = 18;

    private RichText() {
    }

    public static CharSequence render(Context context, String markdown) {
        List<MarkdownDocument.Block> blocks =
                MarkdownDocument.parse(markdown);

        SpannableStringBuilder out = new SpannableStringBuilder();
        int codeBackground = ContextCompat.getColor(
                context,
                R.color.brand_soft_surface_high
        );
        float density = context.getResources()
                .getDisplayMetrics()
                .density;

        for (MarkdownDocument.Block block : blocks) {
            switch (block.kind()) {
                case BLANK:
                    out.append('\n');
                    break;
                case HEADING:
                    appendHeading(out, context, block, codeBackground);
                    break;
                case BULLET:
                    appendBullet(
                            out,
                            context,
                            block,
                            codeBackground,
                            Math.round(BULLET_INDENT_DP * density)
                    );
                    break;
                case CODE:
                    appendCode(out, block, codeBackground);
                    break;
                default:
                    out.append(inline(context, block.text(), codeBackground));
                    out.append('\n');
                    break;
            }
        }

        return trimTrailingNewlines(out);
    }

    private static void appendHeading(
            SpannableStringBuilder out,
            Context context,
            MarkdownDocument.Block block,
            int codeBackground
    ) {
        if (out.length() > 0) {
            out.append('\n');
        }

        int start = out.length();
        out.append(inline(context, block.text(), codeBackground));
        int end = out.length();

        out.setSpan(
                new StyleSpan(Typeface.BOLD),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        float scale = block.level() == 1
                ? 1.26f
                : block.level() == 2 ? 1.14f : 1.06f;

        out.setSpan(
                new RelativeSizeSpan(scale),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        out.append('\n');
    }

    private static void appendBullet(
            SpannableStringBuilder out,
            Context context,
            MarkdownDocument.Block block,
            int codeBackground,
            int indent
    ) {
        int start = out.length();
        out.append("\u2022  ");
        out.append(inline(context, block.text(), codeBackground));
        int end = out.length();

        out.setSpan(
                new LeadingMarginSpan.Standard(0, indent),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        out.append('\n');
    }

    private static void appendCode(
            SpannableStringBuilder out,
            MarkdownDocument.Block block,
            int codeBackground
    ) {
        if (out.length() > 0) {
            out.append('\n');
        }

        String indented = "  " + block.text().replace("\n", "\n  ");

        int start = out.length();
        out.append(indented);
        int end = out.length();

        out.setSpan(
                new TypefaceSpan("monospace"),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        out.setSpan(
                new BackgroundColorSpan(codeBackground),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        out.setSpan(
                new RelativeSizeSpan(0.92f),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        out.append('\n');
    }

    /**
     * Single left-to-right scan over one block, emitting spans as it copies the
     * visible characters. Unmatched markers are left as literal text.
     */
    private static CharSequence inline(
            Context context,
            String text,
            int codeBackground
    ) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        Matcher link = LINK.matcher(text);
        int index = 0;

        while (index < text.length()) {
            link.region(index, text.length());

            if (link.lookingAt()) {
                out.append(link.group(1)).append(" (").append(link.group(2)).append(')');
                index = link.end();
                continue;
            }

            char current = text.charAt(index);
            boolean doubleMarker = index + 1 < text.length()
                    && text.charAt(index + 1) == current;

            if (current == '`') {
                int end = text.indexOf('`', index + 1);

                if (end > index + 1) {
                    int start = out.length();
                    out.append(text, index + 1, end);
                    int stop = out.length();

                    out.setSpan(
                            new TypefaceSpan("monospace"),
                            start,
                            stop,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    out.setSpan(
                            new RelativeSizeSpan(0.95f),
                            start,
                            stop,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    out.setSpan(
                            new BackgroundColorSpan(codeBackground),
                            start,
                            stop,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    index = end + 1;
                    continue;
                }
            }

            if (doubleMarker && (current == '*' || current == '_')) {
                int end = text.indexOf("" + current + current, index + 2);

                if (end > index + 2) {
                    appendSpan(
                            out,
                            text.substring(index + 2, end),
                            new StyleSpan(Typeface.BOLD)
                    );
                    index = end + 2;
                    continue;
                }
            }

            if (current == '~' && doubleMarker) {
                int end = text.indexOf("~~", index + 2);

                if (end > index + 2) {
                    appendSpan(
                            out,
                            text.substring(index + 2, end),
                            new StrikethroughSpan()
                    );
                    index = end + 2;
                    continue;
                }
            }

            if (!doubleMarker && (current == '*' || current == '_')) {
                int end = closingItalic(text, index + 1, current);

                if (end > index + 1) {
                    appendSpan(
                            out,
                            text.substring(index + 1, end),
                            new StyleSpan(Typeface.ITALIC)
                    );
                    index = end + 1;
                    continue;
                }
            }

            out.append(current);
            index++;
        }

        return out;
    }

    private static int closingItalic(String text, int from, char marker) {
        for (int index = from; index < text.length(); index++) {
            if (text.charAt(index) != marker) {
                continue;
            }

            if (index == from || Character.isWhitespace(text.charAt(index - 1))) {
                return -1;
            }

            return index;
        }

        return -1;
    }

    private static void appendSpan(
            SpannableStringBuilder out,
            String content,
            Object span
    ) {
        int start = out.length();
        out.append(content);
        out.setSpan(
                span,
                start,
                out.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
    }

    private static CharSequence trimTrailingNewlines(
            SpannableStringBuilder builder
    ) {
        int end = builder.length();

        while (end > 0 && builder.charAt(end - 1) == '\n') {
            end--;
        }

        return builder.subSequence(0, end);
    }
}
