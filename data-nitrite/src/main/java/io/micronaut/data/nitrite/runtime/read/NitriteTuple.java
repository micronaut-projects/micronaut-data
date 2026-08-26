/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.nitrite.runtime.read;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.nitrite.runtime.ValueConverter;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;

import java.util.List;
import java.util.Map;

/**
 * A Nitrite-backed {@link Tuple} projection.
 *
 * @since 5.2.0
 */
@Internal
final class NitriteTuple implements Tuple {

    private final ValueConverter valueConverter;
    private final Object[] values;
    private final Map<String, Integer> aliasToPosition;

    NitriteTuple(ValueConverter valueConverter, Object[] values, Map<String, Integer> aliasToPosition) {
        this.valueConverter = valueConverter;
        this.values = values;
        this.aliasToPosition = aliasToPosition;
    }

    @Override
    public <X> X get(TupleElement<X> tupleElement) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <X> @Nullable X get(String alias, Class<X> type) {
        return valueConverter.convert(get(alias), type);
    }

    @Override
    public @Nullable Object get(String alias) {
        Integer index = aliasToPosition.get(alias);
        if (index == null) {
            throw new IllegalArgumentException("Unknown alias: " + alias);
        }
        return get(index);
    }

    @Override
    public <X> @Nullable X get(int i, Class<X> type) {
        return valueConverter.convert(get(i), type);
    }

    @Override
    public @Nullable Object get(int i) {
        if (i < 0 || i >= values.length) {
            throw new IllegalArgumentException("Tuple index out of range: " + i);
        }
        return values[i];
    }

    @Override
    public Object[] toArray() {
        return values.clone();
    }

    @Override
    public List<TupleElement<?>> getElements() {
        throw new UnsupportedOperationException();
    }
}
