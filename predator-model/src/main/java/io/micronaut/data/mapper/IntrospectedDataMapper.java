/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.mapper;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;

import java.util.Collection;
import java.util.Optional;

/**
 * A {@link DataMapper} that maps objects using {@link io.micronaut.core.annotation.Introspected} metadata.
 *
 * @param <D> The result set type.
 * @author graemerocher
 * @since 1.0.0
 */
@FunctionalInterface
public interface IntrospectedDataMapper<D> extends DataMapper<D> {

    @Override
    default <R> R map(D resultSet, Class<R> type) throws InstantiationException {
        BeanIntrospection<R> introspection = BeanIntrospection.getIntrospection(type);
        ConversionService<?> conversionService = getConversionService();
        Argument<?>[] arguments = introspection.getConstructorArguments();
        R instance;
        if (ArrayUtils.isEmpty(arguments)) {
            instance = introspection.instantiate();
        } else {
            Object[] args = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                Argument<?> argument = arguments[i];
                Object o = read(resultSet, argument.getName());
                ArgumentConversionContext<?> acc = ConversionContext.of(argument);
                args[i] = conversionService.convert(o, acc).orElseThrow(() -> {
                            Optional<ConversionError> lastError = acc.getLastError();
                            return lastError.<RuntimeException>map(conversionError -> new ConversionErrorException(argument, conversionError))
                                    .orElseGet(() ->
                                            new IllegalArgumentException("Cannot convert object type " + o.getClass() + " to required type: " + argument.getType())
                                    );
                }

                );
            }
            instance = introspection.instantiate(args);
        }
        BeanWrapper<R> wrapper = BeanWrapper.getWrapper(instance);
        Collection<BeanProperty<R, Object>> properties = introspection.getBeanProperties();
        for (BeanProperty<R, Object> property : properties) {
            Object v = read(resultSet, property.getName());
            wrapper.setProperty(property.getName(), v);
        }

        return instance;
    }
}
