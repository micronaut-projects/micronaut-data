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
package io.micronaut.data.runtime.mapper;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.Projection;
import io.micronaut.data.exceptions.DataAccessException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link TypeMapper} that maps objects using a compile time computed {@link BeanIntrospection}.
 *
 * @param <D> The object type.
 * @param <R> The result type
 * @author graemerocher
 * @since 1.0.0
 */
@FunctionalInterface
public interface BeanIntrospectionMapper<D, R> extends TypeMapper<D, R> {

    @Override
    default R map(D object, Class<R> type) throws InstantiationException {
        ArgumentUtils.requireNonNull("resultSet", object);
        ArgumentUtils.requireNonNull("type", type);
        try {
            BeanIntrospection<R> introspection = BeanIntrospection.getIntrospection(type);
            Argument<?>[] arguments = introspection.getConstructorArguments();
            Collection<BeanProperty<R, Object>> beanProperties = introspection.getBeanProperties();
            Map<String, String> projections = CollectionUtils.newLinkedHashMap(beanProperties.size());
            for (BeanProperty<R, Object> beanProperty : beanProperties) {
                String projectionName = beanProperty.getAnnotationMetadata().stringValue(Projection.class).orElse(null);
                if (projectionName != null) {
                    projections.put(beanProperty.getName(), projectionName);
                }
            }

            R instance;
            if (ArrayUtils.isEmpty(arguments)) {
                instance = introspection.instantiate();
            } else {
                Object[] args = new Object[arguments.length];
                for (int i = 0; i < arguments.length; i++) {
                    Argument<?> argument = arguments[i];
                    String argumentName = argument.getName();
                    String name = projections.getOrDefault(argumentName, argumentName);
                    Object o = read(object, name);
                    if (o == null) {
                        args[i] = null;
                    } else if (argument.getType().isInstance(o)) {
                        args[i] = o;
                    } else {
                        Object convertFrom;
                        if (Collection.class.isAssignableFrom(argument.getType()) && !(o instanceof Collection)) {
                            convertFrom = MapperUtils.toCollection(o);
                        } else {
                            convertFrom = o;
                        }
                        args[i] = convert(convertFrom, argument);
                    }
                }
                instance = introspection.instantiate(args);
            }
            for (BeanProperty<R, Object> property : beanProperties) {
                if (property.isReadOnly()) {
                    continue;
                }

                Object v = read(object, property.getName());
                if (v != null) {
                    if (property.getType().isInstance(v)) {
                        property.set(instance, v);
                    } else if (Iterable.class.isAssignableFrom(property.getType())) {
                        if (v instanceof Collection) {
                            property.set(instance, v);
                        } else if (v instanceof Iterable<?> iterable) {
                            List<?> list = new ArrayList<>(CollectionUtils.iterableToList(iterable));
                            property.set(instance, convert(list, property.asArgument()));
                        } else {
                            Collection<?> collection = MapperUtils.toCollection(v);
                            property.set(instance, convert(collection, property.asArgument()));
                        }
                    } else {
                        property.set(instance, convert(v, property.asArgument()));
                    }
                }
            }

            return instance;
        } catch (IntrospectionException | InstantiationException e) {
            throw new DataAccessException("Error instantiating type [" + type.getName() + "] from introspection: " + e.getMessage(), e);
        }
    }

    default Object convert(Object value, Argument<?> argument) {
        ConversionContext acc = ConversionContext.of(argument);
        Optional<?> result = getConversionService().convert(value, argument);
        if (result.isEmpty()) {
            Optional<ConversionError> lastError = acc.getLastError();
            if (lastError.isPresent()) {
                throw new ConversionErrorException(argument, lastError.get());
            }
            throw new IllegalArgumentException("Cannot convert object type " + value.getClass() + " to required type: " + argument.getType());
        }
        return result.get();
    }
}
