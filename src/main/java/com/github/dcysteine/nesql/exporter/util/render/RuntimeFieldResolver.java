package com.github.dcysteine.nesql.exporter.util.render;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Resolves cached runtime fields across MCP development names and SRG production names. */
public final class RuntimeFieldResolver {
    private static final ConcurrentMap<FieldKey, Field> FIELDS =
            new ConcurrentHashMap<FieldKey, Field>();
    private static final Set<FieldKey> MISSING = Collections.newSetFromMap(
            new ConcurrentHashMap<FieldKey, Boolean>());

    private RuntimeFieldResolver() {}

    public static Object read(Object target, String... aliases) {
        if (target == null) {
            return null;
        }
        Field field = find(target.getClass(), aliases);
        if (field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    static Field find(Class<?> owner, String... aliases) {
        if (owner == null || aliases == null || aliases.length == 0) {
            return null;
        }
        FieldKey key = new FieldKey(owner, aliases);
        Field cached = FIELDS.get(key);
        if (cached != null) {
            return cached;
        }
        if (MISSING.contains(key)) {
            return null;
        }

        Class<?> current = owner;
        while (current != null && current != Object.class) {
            for (String alias : aliases) {
                if (alias == null || alias.trim().isEmpty()) {
                    continue;
                }
                try {
                    Field field = current.getDeclaredField(alias);
                    field.setAccessible(true);
                    Field existing = FIELDS.putIfAbsent(key, field);
                    return existing == null ? field : existing;
                } catch (NoSuchFieldException ignored) {
                    // Try the next MCP/SRG alias and then the superclass.
                } catch (RuntimeException inaccessible) {
                    MISSING.add(key);
                    return null;
                }
            }
            current = current.getSuperclass();
        }
        MISSING.add(key);
        return null;
    }

    private static final class FieldKey {
        private final Class<?> owner;
        private final String[] aliases;
        private final int hashCode;

        private FieldKey(Class<?> owner, String[] aliases) {
            this.owner = owner;
            this.aliases = aliases.clone();
            this.hashCode = 31 * owner.hashCode() + Arrays.hashCode(this.aliases);
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof FieldKey)) {
                return false;
            }
            FieldKey other = (FieldKey) value;
            return owner == other.owner && Arrays.equals(aliases, other.aliases);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
