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
package io.micronaut.data.runtime.operations.internal.sql;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.VectorScoringFunctionDialectSupport;
import io.micronaut.data.model.vector.search.ScoringFunction;
import org.jspecify.annotations.Nullable;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.EnumSet;

/**
 * Runtime Micronaut bean implementations of dialect-specific vector scoring-function strategies.
 */
@Internal
final class VectorScoringFunctionDialectSupports {

    private VectorScoringFunctionDialectSupports() {
    }

    /**
     * PostgreSQL vector scoring-function strategy.
     */
    @Singleton
    @Named("POSTGRES")
    static final class Postgres implements VectorScoringFunctionDialectSupport {

        @Override
        public Dialect dialect() {
            return Dialect.POSTGRES;
        }

        @Override
        public EnumSet<ScoringFunction> supportedScoringFunctions() {
            return EnumSet.of(
                ScoringFunction.COSINE,
                ScoringFunction.L2_EUCLIDEAN,
                ScoringFunction.DOT
            );
        }

        @Override
        public @Nullable ScoringFunction defaultScoringFunction() {
            return ScoringFunction.COSINE;
        }

        @Override
        public String adaptQueryForScoringFunction(String query, ScoringFunction selected) {
            String operator = switch (selected) {
                case COSINE -> "<=>";
                case L2_EUCLIDEAN -> "<->";
                case DOT -> "<#>";
                default -> throw new IllegalArgumentException("Scoring function " + selected + " is not supported for PostgreSQL");
            };
            return query.replace("<=>", operator);
        }
    }

    /**
     * Oracle vector scoring-function strategy.
     */
    @Singleton
    @Named("ORACLE")
    static final class Oracle implements VectorScoringFunctionDialectSupport {

        @Override
        public Dialect dialect() {
            return Dialect.ORACLE;
        }

        @Override
        public EnumSet<ScoringFunction> supportedScoringFunctions() {
            return EnumSet.of(
                ScoringFunction.COSINE,
                ScoringFunction.L2_EUCLIDEAN,
                ScoringFunction.L2_EUCLIDEAN_SQUARED,
                ScoringFunction.DOT,
                ScoringFunction.L1_MANHATTAN
            );
        }

        @Override
        public @Nullable ScoringFunction defaultScoringFunction() {
            return ScoringFunction.COSINE;
        }

        @Override
        public String adaptQueryForScoringFunction(String query, ScoringFunction selected) {
            String metric = switch (selected) {
                case COSINE -> "COSINE";
                case L2_EUCLIDEAN -> "EUCLIDEAN";
                case L2_EUCLIDEAN_SQUARED -> "EUCLIDEAN_SQUARED";
                case DOT -> "DOT";
                case L1_MANHATTAN -> "MANHATTAN";
                default -> throw new IllegalArgumentException("Scoring function " + selected + " is not supported for Oracle");
            };
            return query.replace(",COSINE)", "," + metric + ")");
        }
    }

    /**
     * MySQL vector scoring-function strategy.
     */
    @Singleton
    @Named("MYSQL")
    static final class MySql implements VectorScoringFunctionDialectSupport {

        @Override
        public Dialect dialect() {
            return Dialect.MYSQL;
        }

        @Override
        public EnumSet<ScoringFunction> supportedScoringFunctions() {
            return EnumSet.of(
                ScoringFunction.COSINE,
                ScoringFunction.L2_EUCLIDEAN,
                ScoringFunction.DOT
            );
        }

        @Override
        public @Nullable ScoringFunction defaultScoringFunction() {
            return ScoringFunction.L2_EUCLIDEAN;
        }

        @Override
        public String adaptQueryForScoringFunction(String query, ScoringFunction selected) {
            String metric = switch (selected) {
                case COSINE -> "COSINE";
                case L2_EUCLIDEAN -> "EUCLIDEAN";
                case DOT -> "DOT";
                default -> throw new IllegalArgumentException("Scoring function " + selected + " is not supported for MySQL");
            };
            return query.replace(",'EUCLIDEAN')", ",'" + metric + "')");
        }
    }
}
