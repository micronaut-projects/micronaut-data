/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.runtime.QueryParameterBinding;

import java.util.stream.Collectors;

/**
 * The basic {@link io.micronaut.data.model.runtime.StoredQuery} created from {@link QueryResult}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
final class QueryResultStoredQuery<E, R> extends BasicStoredQuery<E, R> {

    public QueryResultStoredQuery(QueryResult queryResult, Class<E> rootEntity, Class<R> resultType) {
        super(
                queryResult.getQuery(),
                queryResult.getParameterBindings().stream()
                        .anyMatch(io.micronaut.data.model.query.builder.QueryParameterBinding::isExpandable) ? queryResult.getQueryParts().toArray(new String[0]) : null,
                queryResult.getParameterBindings().stream().map(QueryResultStoredQuery::map).collect(Collectors.toList()),
                rootEntity,
                resultType
        );
    }

    private static QueryParameterBinding map(io.micronaut.data.model.query.builder.QueryParameterBinding binding) {
        return new QueryParameterBinding() {

            @Override
            public String getName() {
                return binding.getKey();
            }

            @Override
            public DataType getDataType() {
                return binding.getDataType();
            }

            @Override
            public Class<?> getParameterConverterClass() {
                if (binding.getConverterClassName() == null) {
                    return null;
                }
                return ClassUtils.forName(binding.getConverterClassName(), null).orElseThrow(IllegalStateException::new);
            }

            @Override
            public int getParameterIndex() {
                return binding.getParameterIndex();
            }

            @Override
            public String[] getParameterBindingPath() {
                return binding.getParameterBindingPath();
            }

            @Override
            public String[] getPropertyPath() {
                return binding.getPropertyPath();
            }

            @Override
            public boolean isAutoPopulated() {
                return binding.isAutoPopulated();
            }

            @Override
            public boolean isRequiresPreviousPopulatedValue() {
                return binding.isRequiresPreviousPopulatedValue();
            }

            @Override
            public QueryParameterBinding getPreviousPopulatedValueParameter() {
                return null;
            }
        };
    }
}
