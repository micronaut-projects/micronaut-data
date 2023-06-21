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
package io.micronaut.data.runtime.convert;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.DefaultMutableConversionService;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverter;

import java.util.Optional;
import java.util.function.Function;

/**
 * The implementation of {@link DataConversionService} which combines the Data project only converters
 * and shared converters from the Core.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
final class DataConversionServiceImpl implements DataConversionService {

    private final DefaultMutableConversionService internalConversionService = new DefaultMutableConversionService();
    private final ConversionService sharedConversionService;

    DataConversionServiceImpl(ConversionService sharedConversionService) {
        this.sharedConversionService = sharedConversionService;
    }

    <S, T> void addConverter(Class<S> sourceType, Class<T> targetType, Function<S, T> typeConverter) {
        internalConversionService.addConverter(sourceType, targetType, typeConverter);
    }

    <S, T> void addConverter(Class<S> sourceType, Class<T> targetType, TypeConverter<S, T> typeConverter) {
        internalConversionService.addConverter(sourceType, targetType, typeConverter);
    }

    MutableConversionService getMutableConversionService() {
        return internalConversionService;
    }

    @Override
    public <T> Optional<T> convert(Object object, Class<T> targetType, ConversionContext context) {
        Optional<T> result = internalConversionService.convert(object, targetType, context);
        if (result.isPresent()) {
            return result;
        }
        return sharedConversionService.convert(object, targetType, context);
    }

    @Override
    public <S, T> boolean canConvert(Class<S> sourceType, Class<T> targetType) {
        return internalConversionService.canConvert(sourceType, targetType)
                || sharedConversionService.canConvert(sourceType, targetType);
    }

}
