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
package io.micronaut.data.nitrite.model.query.builder;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.query.builder.QueryParameterBinding;

import java.util.List;
import java.util.Map;

/**
 * The result of {@link NitriteQueryBuilder#buildRuntimeFilter}: the filter, sort and projection
 * maps built directly from a runtime Criteria query, with no JSON serialize/reparse round trip.
 * Values keep their original Java types (Integer, Long, BigDecimal, ...) exactly as produced by
 * {@link NitritePredicateVisitor} — nothing is lost or narrowed the way it would be if the filter
 * had to survive a text round trip.
 *
 * @param filter the filter map, keyed by field name (or the empty map for "match all")
 * @param sort the sort map, keyed by field name to 1 (ascending) or -1 (descending)
 * @param projection the projection map, keyed by field name to 1/true when included
 * @param offset the offset, or 0 if unset
 * @param limit the limit, or -1 if unset
 * @param parameterBindings the parameter bindings referenced by the filter
 * @since 5.2.0
 */
@Internal
public record NitriteRuntimeFilter(
    Map<String, Object> filter,
    Map<String, Object> sort,
    Map<String, Object> projection,
    int offset,
    int limit,
    List<QueryParameterBinding> parameterBindings) {
}
