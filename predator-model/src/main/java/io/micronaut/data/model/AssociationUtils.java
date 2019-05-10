package io.micronaut.data.model;

import io.micronaut.core.annotation.Internal;

import java.util.regex.Pattern;

/**
 * Internal association utilities.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
class AssociationUtils {
    /**
     * The split pattern for camel case.
     */
    static final Pattern CAMEL_CASE_SPLIT_PATTERN = Pattern.compile("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");
}
