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
     * Resolves the default scoring function for a SQL dialect.
     *
     * @param dialect The SQL dialect
     * @return The dialect default scoring function, or {@code null} if none applies
     */
    static @Nullable ScoringFunction defaultScoringFunctionForDialect(Dialect dialect) {
        return switch (dialect) {
            case POSTGRES, ORACLE -> ScoringFunction.COSINE;
            case MYSQL -> ScoringFunction.L2_EUCLIDEAN;
            default -> null;
        };
    }

    /**
     * Returns the default scoring function for a query: {@link ScoringFunction#HAMMING} when the query
     * compares Oracle binary vectors, otherwise {@link #defaultScoringFunction()}.
     *
     * @param query The query SQL
     * @return The default scoring function for the query, or {@code null} if not applicable
     * @since 5.3.0
     */
    default @Nullable ScoringFunction defaultScoringFunction(String query) {
        return defaultScoringFunctionForQuery(dialect(), query, defaultScoringFunction());
    }

    /**
     * Resolves the default scoring function for a query: {@link ScoringFunction#HAMMING} when the query
     * compares Oracle binary vectors, otherwise the dialect default.
     *
     * @param dialect The SQL dialect
     * @param query The query SQL
     * @return The default scoring function for the query, or {@code null} if none applies
     * @since 5.3.0
     */
    static @Nullable ScoringFunction defaultScoringFunctionForQuery(Dialect dialect, @Nullable String query) {
        return defaultScoringFunctionForQuery(dialect, query, defaultScoringFunctionForDialect(dialect));
    }

    private static @Nullable ScoringFunction defaultScoringFunctionForQuery(Dialect dialect,
                                                                           @Nullable String query,
                                                                           @Nullable ScoringFunction dialectDefault) {
        if (dialect == Dialect.ORACLE && query != null && query.contains(VectorSimilarityDialect.ORACLE_HAMMING_DISTANCE_SUFFIX)) {
            return ScoringFunction.HAMMING;
        }
        return dialectDefault;
    }

    /**
     * Adapts a derived vector-search query for the selected scoring function.
     *
     * @param query The original query SQL
     * @param selected The scoring function to apply
     * @return The adapted query SQL
     */
    String adaptQueryForScoringFunction(String query, ScoringFunction selected);
}
