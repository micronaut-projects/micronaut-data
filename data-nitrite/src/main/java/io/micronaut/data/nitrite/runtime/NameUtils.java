package io.micronaut.data.nitrite.runtime;

import io.micronaut.core.annotation.Internal;

import java.util.stream.IntStream;

/**
 * Shared string naming conversions between camelCase and snake_case.
 *
 * @since 5.0.0
 */
@Internal
public final class NameUtils {

    private NameUtils() {
    }

    /**
     * Convert a camelCase string to snake_case.
     *
     * @param value the camelCase value
     * @return the snake_case value
     */
    public static String camelToSnake(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        StringBuilder result = new StringBuilder(value.length() + 4);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(ch));
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    /**
     * Convert a snake_case string to camelCase.
     *
     * @param value the snake_case value
     * @return the camelCase value
     */
    public static String snakeToCamel(String value) {
        if (value == null || value.indexOf('_') < 0) {
            return value;
        }
        StringBuilder result = new StringBuilder(value.length());
        boolean upperNext = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '_') {
                upperNext = true;
            } else if (upperNext) {
                result.append(Character.toUpperCase(ch));
                upperNext = false;
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    /**
     * Convert each segment of a dot-separated snake_case path to camelCase.
     *
     * @param path the dot-separated snake_case path
     * @return the camelCase path
     */
    public static String snakeToCamelPath(String path) {
        if (path == null || path.indexOf('_') < 0) {
            return path;
        }
        String[] parts = path.split("\\.");
        IntStream.range(0, parts.length).forEach(i -> parts[i] = snakeToCamel(parts[i]));
        return String.join(".", parts);
    }
}
