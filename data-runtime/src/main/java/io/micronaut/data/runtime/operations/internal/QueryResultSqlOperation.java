/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.runtime.operations.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.QueryParameterBinding;

import java.util.stream.Collectors;

/**
 * Implementation of {@link StoredSqlOperation} that retrieves data from {@link QueryResult}.
 */
@Internal
public class QueryResultSqlOperation extends StoredSqlOperation {

    /**
     * Creates a new instance.
     *
     * @param queryBuilder       The queryBuilder
     * @param queryResult        The query result
     */
    public QueryResultSqlOperation(SqlQueryBuilder queryBuilder, QueryResult queryResult) {
        super(queryBuilder,
                queryResult.getQuery(),
                queryResult.getParameterBindings().stream().anyMatch(io.micronaut.data.model.query.builder.QueryParameterBinding::isExpandable) ? queryResult.getQueryParts().toArray(new String[0]) : null,
                queryResult.getParameterBindings().stream().map(QueryResultSqlOperation::map).collect(Collectors.toList()),
                false);
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
