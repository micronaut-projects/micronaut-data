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
import jakarta.inject.Singleton;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the vector scoring-function strategy for a SQL dialect.
 */
@Internal
@Singleton
final class VectorScoringSupportResolver {

    private final Map<Dialect, VectorScoringFunctionDialectSupport> byDialect;

    /**
     * @param supports All discovered dialect strategy beans
     */
    VectorScoringSupportResolver(List<VectorScoringFunctionDialectSupport> supports) {
        EnumMap<Dialect, VectorScoringFunctionDialectSupport> map = new EnumMap<>(Dialect.class);
        for (VectorScoringFunctionDialectSupport support : supports) {
            map.put(support.dialect(), support);
        }
        byDialect = map;
    }

    /**
     * @param dialect The target SQL dialect
     * @return Matching strategy or a default no-op strategy when not available
     */
    VectorScoringFunctionDialectSupport resolve(Dialect dialect) {
        return byDialect.getOrDefault(dialect, DefaultVectorScoringFunctionDialectSupport.INSTANCE);
    }
}
