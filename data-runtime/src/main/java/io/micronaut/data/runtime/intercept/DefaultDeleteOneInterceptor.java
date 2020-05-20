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
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.DeleteOneInterceptor;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.runtime.BatchOperation;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.operations.RepositoryOperations;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;

/**
 * The default implementation of {@link DeleteOneInterceptor}.
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultDeleteOneInterceptor<T> extends AbstractQueryInterceptor<T, Object> implements DeleteOneInterceptor<T> {

    /**
     * Default constructor.
     * @param datastore The operations
     */
    protected DefaultDeleteOneInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, Object> context) {
        Object[] parameterValues = context.getParameterValues();
        if (parameterValues.length == 1) {
            Object o = parameterValues[0];
            if (context.hasAnnotation(Query.class)) {
                PreparedQuery<?, Number> preparedQuery = (PreparedQuery<?, Number>) prepareQuery(methodKey, context);
                final Class<?> rootEntity = preparedQuery.getRootEntity();
                if (rootEntity.isInstance(o)) {
                    final RuntimePersistentEntity<?> entity = operations.getEntity(rootEntity);
                    final RuntimePersistentProperty<?> identity = entity.getIdentity();
                    if (identity != null) {
                        if (identity instanceof Embedded) {
                            final BeanProperty idProp = identity.getProperty();
                            final Object idValue = idProp.get(o);
                            if (idValue == null) {
                                throw new IllegalStateException("Cannot delete an entity with null ID: " + o);
                            }
                            preparedQuery.getParameterArray()[0] = idValue;
                        }
                        final Number result = operations.executeDelete(preparedQuery).orElse(0);
                        final Class<Object> returnType = context.getReturnType().getType();
                        if (returnType.equals(rootEntity)) {
                            if (result.longValue() > 0) {
                                return o;
                            } else {
                                return null;
                            }
                        } else if (Number.class.isAssignableFrom(returnType)) {
                            return ConversionService.SHARED.convertRequired(result, returnType);
                        } else  {
                            return result;
                        }
                    } else {
                        throw new IllegalStateException("Cannot delete an entity which defines no ID: " + rootEntity.getName());
                    }
                } else {
                    if (o == null) {
                        throw new IllegalArgumentException("Entity to delete cannot be null");
                    } else {
                        throw new IllegalArgumentException("Entity argument must be an instance of " + rootEntity.getName());
                    }
                }
            } else {

                BatchOperation<Object> batchOperation = getBatchOperation(context, Collections.singletonList(o));
                if (o != null) {
                    operations.deleteAll(batchOperation);
                } else {
                    throw new IllegalArgumentException("Entity to delete cannot be null");
                }
            }
        } else {
            throw new IllegalStateException("Expected exactly one argument");
        }

        return null;
    }
}
