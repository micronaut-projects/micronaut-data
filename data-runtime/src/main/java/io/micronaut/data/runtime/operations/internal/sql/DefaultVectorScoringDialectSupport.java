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
import io.micronaut.data.model.query.builder.sql.VectorScoringDialectSupport;
import io.micronaut.data.model.vector.search.ScoringFunction;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * Default no-op vector scoring-function strategy used when a dialect-specific strategy is unavailable.
 */
@Internal
final class DefaultVectorScoringDialectSupport implements VectorScoringDialectSupport {

    static final DefaultVectorScoringDialectSupport INSTANCE = new DefaultVectorScoringDialectSupport();

    private DefaultVectorScoringDialectSupport() {
    }

    @Override
    public Dialect dialect() {
        return Dialect.ANSI;
    }

    @Override
    public EnumSet<ScoringFunction> supportedScoringFunctions() {
        return EnumSet.noneOf(ScoringFunction.class);
    }

    @Override
    public @Nullable ScoringFunction defaultScoringFunction() {
        return null;
    }

    @Override
    public String adaptQueryForScoringFunction(String query, ScoringFunction selected) {
        return query;
    }
}
