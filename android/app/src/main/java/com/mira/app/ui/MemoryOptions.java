package com.mira.app.ui;

import android.content.Context;

import com.mira.app.R;

public final class MemoryOptions {

    public static final String FIELD_DEPTH = "explanation_depth";
    public static final String FIELD_TONE = "tone";
    public static final String FIELD_EXAMPLES = "examples";
    public static final String FIELD_FORMATTING = "formatting";
    public static final String FIELD_TEACHING = "teaching_sequence";

    public static final String[] FIELDS = {
            FIELD_DEPTH,
            FIELD_TONE,
            FIELD_EXAMPLES,
            FIELD_FORMATTING,
            FIELD_TEACHING
    };

    private static final String NO_PREFERENCE = "no_strong_preference";

    private static final String[] DEPTH_VALUES = {
            "", "concise", "balanced", "detailed"
    };

    private static final String[] TONE_VALUES = {
            "", "conversational", "formal", "academic"
    };

    private static final String[] EXAMPLES_VALUES = {
            "", "prefer_concrete_examples", "theory_first"
    };

    private static final String[] FORMATTING_VALUES = {
            "", "structured", "prose", "bulleted_lists"
    };

    private static final String[] TEACHING_VALUES = {
            "", "principles_first", "code_first", "analogy_first"
    };

    private MemoryOptions() {
    }

    public static String[] values(String field) {
        switch (field) {
            case FIELD_DEPTH:
                return DEPTH_VALUES;
            case FIELD_TONE:
                return TONE_VALUES;
            case FIELD_EXAMPLES:
                return EXAMPLES_VALUES;
            case FIELD_FORMATTING:
                return FORMATTING_VALUES;
            case FIELD_TEACHING:
                return TEACHING_VALUES;
            default:
                return new String[]{""};
        }
    }

    public static String[] labels(Context context, String field) {
        int[] ids = labelIds(field);
        String[] labels = new String[ids.length];

        for (int index = 0; index < ids.length; index++) {
            labels[index] = context.getString(ids[index]);
        }

        return labels;
    }

    public static int indexOf(String field, String value) {
        if (value == null
                || value.isBlank()
                || NO_PREFERENCE.equals(value)) {
            return 0;
        }

        String[] values = values(field);

        for (int index = 1; index < values.length; index++) {
            if (values[index].equals(value)) {
                return index;
            }
        }

        return -1;
    }

    public static String fieldLabel(Context context, String field) {
        switch (field) {
            case FIELD_DEPTH:
                return context.getString(R.string.memory_field_depth);
            case FIELD_TONE:
                return context.getString(R.string.memory_field_tone);
            case FIELD_EXAMPLES:
                return context.getString(R.string.memory_field_examples);
            case FIELD_FORMATTING:
                return context.getString(R.string.memory_field_formatting);
            case FIELD_TEACHING:
                return context.getString(R.string.memory_field_teaching);
            default:
                return field;
        }
    }

    public static String valueLabel(
            Context context,
            String field,
            String value
    ) {
        int index = indexOf(field, value);

        if (index < 0) {
            return value;
        }

        return labels(context, field)[index];
    }

    private static int[] labelIds(String field) {
        switch (field) {
            case FIELD_DEPTH:
                return new int[]{
                        R.string.memory_no_preference,
                        R.string.memory_value_concise,
                        R.string.memory_value_balanced,
                        R.string.memory_value_detailed
                };
            case FIELD_TONE:
                return new int[]{
                        R.string.memory_no_preference,
                        R.string.memory_value_conversational,
                        R.string.memory_value_formal,
                        R.string.memory_value_academic
                };
            case FIELD_EXAMPLES:
                return new int[]{
                        R.string.memory_no_preference,
                        R.string.memory_value_concrete_examples,
                        R.string.memory_value_theory_first
                };
            case FIELD_FORMATTING:
                return new int[]{
                        R.string.memory_no_preference,
                        R.string.memory_value_structured,
                        R.string.memory_value_prose,
                        R.string.memory_value_bulleted_lists
                };
            case FIELD_TEACHING:
                return new int[]{
                        R.string.memory_no_preference,
                        R.string.memory_value_principles_first,
                        R.string.memory_value_code_first,
                        R.string.memory_value_analogy_first
                };
            default:
                return new int[]{
                        R.string.memory_no_preference
                };
        }
    }
}