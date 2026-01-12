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
package io.micronaut.data.runtime.query;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.runtime.query.internal.DefaultPreparedQuery;
import org.jspecify.annotations.Nullable;

/**
 * Default prepared query resolver.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
public abstract class DefaultPreparedQueryResolver implements PreparedQueryResolver {

    @Override
    public <E, R> PreparedQuery<E, R> resolveQuery(MethodInvocationContext<?, ?> context,
                                                   StoredQuery<E, R> storedQuery,
                                                   Pageable pageable) {
        return new DefaultPreparedQuery<>(
                context,
                storedQuery,
                storedQuery.getQuery(),
                pageable,
                DefaultPreparedQuery.getParameterInRole(TypeRole.LIMIT, Limit.class, context, getConversionService()).orElse(Limit.UNLIMITED),
                storedQuery.isDtoProjection(),
                getConversionService()
        );
    }

    @Override
    public <E, R> PreparedQuery<E, R> resolveCountQuery(MethodInvocationContext<?, ?> context,
                                                        StoredQuery<E, R> storedQuery,
                                                        @Nullable Pageable pageable) {
        return new DefaultPreparedQuery<>(
                context,
                storedQuery,
                storedQuery.getQuery(),
                pageable == null ? Pageable.UNPAGED : pageable,
                Limit.UNLIMITED,
                false,
                getConversionService()
        );
    }

    protected abstract ConversionService getConversionService();

}
