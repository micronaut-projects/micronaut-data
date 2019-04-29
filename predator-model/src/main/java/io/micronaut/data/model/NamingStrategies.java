package io.micronaut.data.model;

import io.micronaut.core.naming.NameUtils;

import javax.annotation.Nonnull;
import java.util.function.Function;

/**
 * Naming strategy enum for when a class or property name has no explicit mapping.
 *
 * @author graemerocher
 * @since 1.0
 */
public enum NamingStrategies implements NamingStrategy {
    /**
     * Example: FOO_BAR
     */
    UNDER_SCORED_SEPARATED_UPPER_CASE(NameUtils::environmentName),
    /**
     * Example: foo_bar
     */
    UNDER_SCORED_SEPARATED_LOWER_CASE(NameUtils::underscoreSeparate),
    /**
     * Example: foo-bar
     */
    HYPHENATED_SEPARATED_LOWER_CASE(NameUtils::hyphenate),
    /**
     * Example: foobar
     */
    LOWER_CASE(String::toLowerCase),
    /**
     * Example: foobar
     */
    UPPER_CASE(String::toUpperCase),
    /**
     * No naming conversion
     */
    RAW((str) -> str);

    private final Function<String, String> mapper;

    NamingStrategies(Function<String, String> mapper) {
        this.mapper = mapper;
    }

    @Nonnull
    @Override
    public String mappedName(@Nonnull String name) {
        return mapper.apply(name);
    }
}
