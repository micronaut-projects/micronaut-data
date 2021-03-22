/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.DeleteAllInterceptor;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.operations.RepositoryOperations;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.model.runtime.PreparedQuery;

import java.util.Optional;

/**
 * Default implementation of {@link DeleteAllInterceptor}.
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultDeleteAllInterceptor<T> extends AbstractQueryInterceptor<T, Number> implements DeleteAllInterceptor<T> {

    private final DefaultDeleteOneInterceptor deleteOneInterceptor;

    /**
     * Default constructor.
     * @param datastore The operations
     */
    protected DefaultDeleteAllInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
        deleteOneInterceptor = new DefaultDeleteOneInterceptor(datastore);
    }

    @Override
    public Number intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, Number> context) {
        Argument<Number> resultType = context.getReturnType().asArgument();
        Optional<Iterable<Object>> deleteEntities = findEntitiesParameter(context, Object.class);
        Optional<Object> deleteEntity = findEntityParameter(context, Object.class);
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<?, Number> preparedQuery = (PreparedQuery<?, Number>) prepareQuery(methodKey, context);
            if (deleteEntities.isPresent()) {
                final RuntimePersistentProperty<?> identity = operations.getEntity(preparedQuery.getRootEntity()).getIdentity();
                if (identity instanceof Embedded) {
                    int deleteCount = 0;
                    final BeanProperty idProp = identity.getProperty();
                    for (Object o : deleteEntities.get()) {
                        final Object idValue = idProp.get(o);
                        if (idValue == null) {
                            throw new IllegalStateException("Cannot delete an entity with null ID: " + o);
                        }
                        preparedQuery.getParameterArray()[0] = idValue;
                        operations.executeDelete(preparedQuery);
                        deleteCount++;
                    }
                    return convertIfNecessary(resultType, deleteCount);
                }
                Number result = operations.executeDelete(preparedQuery).orElse(0);
                return convertIfNecessary(resultType, result);
            } else {
                Number result = operations.executeDelete(preparedQuery).orElse(0);
                return convertIfNecessary(resultType, result);
            }
        } else if (!deleteEntity.isPresent() && !deleteEntities.isPresent()){
            Class<?> rootEntity = getRequiredRootEntity(context);
            DeleteBatchOperation<?> batchOperation = getDeleteAllBatchOperation(context, rootEntity);
            Number result = operations.deleteAll(batchOperation).orElse(0);
            return convertIfNecessary(resultType, result);
        } else {
            return (Number) deleteOneInterceptor.intercept(methodKey, context);
        }
    }

    private Number convertIfNecessary(Argument<Number> resultType, Number result) {
        if (!resultType.getType().isInstance(result)) {
            return ConversionService.SHARED.convert(result, resultType).orElse(0);
        } else {
            return result;
        }
    }
}
