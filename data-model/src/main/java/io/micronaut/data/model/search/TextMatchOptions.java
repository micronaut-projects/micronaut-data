/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.model.search;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The options of a full-text match beyond its {@link MatchMode}: query-time boosts of the searched
 * properties and the maximum edit distance of a {@link MatchMode#FUZZY} match. A store that cannot
 * honour an option throws {@link UnsupportedSearchFeatureException} with the matching
 * {@link SearchCapability}.
 *
 * @param boosts The boost factor by property name; a boosted property replaces its index-time weight
 * @param fuzziness The maximum edit distance of a fuzzy match, {@code 0} for the store default
 * @author Graeme Rocher
 * @since 5.4.0
 */
@Experimental
public record TextMatchOptions(Map<String, Double> boosts, int fuzziness) {

    /**
     * No boosts and the default fuzziness.
     */
    public static final TextMatchOptions NONE = new TextMatchOptions(Map.of(), 0);

    public TextMatchOptions {
        boosts = Collections.unmodifiableMap(new LinkedHashMap<>(boosts));
        if (fuzziness < 0) {
            throw new IllegalArgumentException("The fuzziness cannot be negative: " + fuzziness);
        }
    }

    /**
     * @return Whether the options are the defaults
     */
    public boolean isDefault() {
        return boosts.isEmpty() && fuzziness == 0;
    }

    /**
     * @param propertyName The property name
     * @return The boost of the property, or {@code null} when it is not boosted
     */
    @Nullable
    public Double boostFor(String propertyName) {
        return boosts.get(propertyName);
    }
}
