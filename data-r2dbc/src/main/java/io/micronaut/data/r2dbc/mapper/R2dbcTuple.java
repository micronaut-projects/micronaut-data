/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.data.r2dbc.mapper;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * An implementation of {@link Tuple}.
 *
 * @author Denis Stepanov
 * @since 4.10
 */
@Internal
final class R2dbcTuple implements Tuple {

    private final ConversionService conversionService;
    @Nullable
    private final Object[] values;
    private final Map<String, Integer> aliasToPosition;

    public R2dbcTuple(ConversionService conversionService, @Nullable Object[] values, Map<String, Integer> aliasToPosition) {
        this.conversionService = conversionService;
        this.values = values;
        this.aliasToPosition = aliasToPosition;
    }

    @Override
    public <X> X get(TupleElement<X> tupleElement) {
        throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable <X> X get(String alias, Class<X> type) {
        return conversionService.convertRequired(get(alias), type);
    }

    @Override
    @Nullable
    public Object get(String alias) {
        Integer index = aliasToPosition.get(alias);
        if (index == null) {
            throw new IllegalArgumentException("Unknown alias: " + alias);
        }
        return get(index);
    }

    @Override
    public @Nullable <X> X get(int i, Class<X> type) {
        return conversionService.convertRequired(get(i), type);
    }

    @Override
    @Nullable
    public Object get(int i) {
        return values[i];
    }

    @Override
    @Nullable
    public Object[] toArray() {
        return values;
    }

    @Override
    public List<TupleElement<?>> getElements() {
        throw new UnsupportedOperationException();
    }
}
