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

import java.util.ArrayList;
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
    private final List<TupleElement<?>> elements;

    NitriteTuple(ValueConverter valueConverter,
                 Object[] values,
                 Map<String, Integer> aliasToPosition,
                 List<String> elementAliases,
                 List<Class<?>> elementJavaTypes) {
        this.valueConverter = valueConverter;
        this.values = values;
        this.aliasToPosition = aliasToPosition;
        List<TupleElement<?>> tupleElements = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            String alias = i < elementAliases.size() ? elementAliases.get(i) : null;
            Object value = values[i];
            Class<?> javaType = i < elementJavaTypes.size()
                ? elementJavaTypes.get(i)
                : value == null ? Object.class : value.getClass();
            tupleElements.add(new NitriteTupleElement(alias, javaType));
        }
        this.elements = List.copyOf(tupleElements);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <X> @Nullable X get(TupleElement<X> tupleElement) {
        int index = elements.indexOf(tupleElement);
        if (index < 0) {
            throw new IllegalArgumentException("Unknown tuple element: " + tupleElement);
        }
        return (X) get(index);
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
        return elements;
    }

    /**
     * A tuple element carrying the selection alias, or {@code null} when the selection had none.
     *
     * @param alias The selection alias
     * @param javaType The runtime type of the projected value
     */
    private record NitriteTupleElement(@Nullable String alias, Class<?> javaType) implements TupleElement<Object> {

        @Override
        public Class<? extends Object> getJavaType() {
            return javaType;
        }

        @Override
        public @Nullable String getAlias() {
            return alias;
        }
    }
}
