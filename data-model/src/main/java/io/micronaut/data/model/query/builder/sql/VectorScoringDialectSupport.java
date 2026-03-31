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
package io.micronaut.data.model.query.builder.sql;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.vector.search.ScoringFunction;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * Dialect strategy for vector scoring-function defaults, validation, and SQL adaptation.
 * This is an internal runtime/processor contract and is not intended for user implementation.
 *
 * @since 5.0.0
 */
@Internal
public interface VectorScoringDialectSupport {

    /** Synthetic function token used for derived vector score expressions. */
    String SCORE_FUNCTION = "mn_vector_score";

    /**
     * @return The dialect this strategy supports.
     */
    Dialect dialect();

    /**
     * @return Supported scoring functions for the dialect.
     */
    EnumSet<ScoringFunction> supportedScoringFunctions();

    /**
     * @return The default scoring function for the dialect, or {@code null} if not applicable.
     */
    @Nullable
    ScoringFunction defaultScoringFunction();

    /**
     * Adapts a derived vector-search query for the selected scoring function.
     *
     * @param query The original query SQL
     * @param selected The scoring function to apply
     * @return The adapted query SQL
     */
    String adaptQueryForScoringFunction(String query, ScoringFunction selected);
}
