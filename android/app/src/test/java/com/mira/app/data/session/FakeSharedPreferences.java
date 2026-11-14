package com.mira.app.data.session;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class FakeSharedPreferences implements SharedPreferences {

    private final Map<String, Object> values = new HashMap<>();

    @Override
    public Map<String, ?> getAll() {
        return new HashMap<>(values);
    }

    @Override
    public String getString(String key, String defaultValue) {
        Object value = values.get(key);

        if (value instanceof String) {
            return (String) value;
        }

        return defaultValue;
    }

    @Override
    public Set<String> getStringSet(
            String key,
            Set<String> defaultValues
    ) {
        Object value = values.get(key);

        if (value instanceof Set<?>) {
            Set<String> copy = new HashSet<>();

            for (Object item : (Set<?>) value) {
                if (item instanceof String) {
                    copy.add((String) item);
                }
            }

            return copy;
        }

        return defaultValues;
    }

    @Override
    public int getInt(String key, int defaultValue) {
        Object value = values.get(key);

        if (value instanceof Integer) {
            return (Integer) value;
        }

        return defaultValue;
    }

    @Override
    public long getLong(String key, long defaultValue) {
        Object value = values.get(key);

        if (value instanceof Long) {
            return (Long) value;
        }

        return defaultValue;
    }

    @Override
    public float getFloat(String key, float defaultValue) {
        Object value = values.get(key);

        if (value instanceof Float) {
            return (Float) value;
        }

        return defaultValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = values.get(key);

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        return defaultValue;
    }

    @Override
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new FakeEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener
    ) {
        // Not needed for token storage tests.
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener
    ) {
        // Not needed for token storage tests.
    }

    private final class FakeEditor implements Editor {

        private final Map<String, Object> updates = new HashMap<>();
        private final Set<String> removals = new HashSet<>();
        private boolean clear;

        @Override
        public Editor putString(String key, String value) {
            if (value == null) {
                removals.add(key);
            } else {
                updates.put(key, value);
            }

            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> values) {
            if (values == null) {
                removals.add(key);
            } else {
                updates.put(key, new HashSet<>(values));
            }

            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            updates.put(key, value);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            updates.put(key, value);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            updates.put(key, value);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            updates.put(key, value);
            return this;
        }

        @Override
        public Editor remove(String key) {
            removals.add(key);
            return this;
        }

        @Override
        public Editor clear() {
            clear = true;
            return this;
        }

        @Override
        public boolean commit() {
            apply();
            return true;
        }

        @Override
        public void apply() {
            if (clear) {
                values.clear();
            }

            for (String key : removals) {
                values.remove(key);
            }

            values.putAll(updates);
            updates.clear();
            removals.clear();
            clear = false;
        }
    }
}
