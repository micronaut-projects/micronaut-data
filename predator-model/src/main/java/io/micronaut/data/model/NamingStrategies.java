/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.ArgumentUtils;

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

    /**
     * Default constructor.
     * @param mapper The mapper
     */
    NamingStrategies(@NonNull Function<String, String> mapper) {
        ArgumentUtils.requireNonNull("mapper", mapper);
        this.mapper = mapper;
    }

    @NonNull
    @Override
    public String mappedName(@NonNull String name) {
        ArgumentUtils.requireNonNull("name", name);
        return mapper.apply(name);
    }
}
