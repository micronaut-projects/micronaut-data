/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.runtime.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.UpdateInterceptor;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.model.runtime.PreparedQuery;

/**
 * Default implementation of {@link UpdateInterceptor}.
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultUpdateInterceptor<T> extends AbstractQueryInterceptor<T, Object> implements UpdateInterceptor<T> {

    /**
     * Default constructor.
     * @param datastore The operations
     */
    public DefaultUpdateInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, Object> context) {
        PreparedQuery<?, Number> preparedQuery = (PreparedQuery<?, Number>) prepareQuery(methodKey, context);
        Number number = operations.executeUpdate(preparedQuery).orElse(null);
        final Argument<Object> returnType = context.getReturnType().asArgument();
        final Class<Object> type = ReflectionUtils.getWrapperType(returnType.getType());
        if (Number.class.isAssignableFrom(type)) {
            if (type.isInstance(number)) {
                return number;
            } else {
                return ConversionService.SHARED.
                        convert(number, returnType)
                        .orElse(0);
            }
        } else if (Boolean.class.isAssignableFrom(type)) {
            return number == null || number.longValue() < 0;
        } else {
            return null;
        }
    }
}
