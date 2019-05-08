package io.micronaut.data.model;

import io.micronaut.core.annotation.Internal;

import java.util.regex.Pattern;

@Internal
class AssociationUtils {
    static final Pattern CAMEL_CASE_SPLIT_PATTERN = Pattern.compile("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");
}
