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
package io.micronaut.data.search.document.runtime;

import io.micronaut.aop.InvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.runtime.operations.internal.query.DefaultBindableParametersStoredQuery;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.model.vector.search.Score;
import io.micronaut.data.search.document.query.DocumentSearchQueryBuilder;
import io.micronaut.json.JsonMapper;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A stored query whose JSON body is parsed once and whose parameter placeholders are replaced per
 * invocation.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @since 5.4.0
 */
@Internal
public final class DocumentStoredQuery<E, R> extends DefaultBindableParametersStoredQuery<E, R> {

    private final Map<String, Object> body;

    /**
     * @param storedQuery The stored query
     * @param persistentEntity The entity
     * @param conversionService The conversion service
     * @param jsonMapper The JSON mapper
     */
    public DocumentStoredQuery(StoredQuery<E, R> storedQuery,
                               RuntimePersistentEntity<E> persistentEntity,
                               ConversionService conversionService,
                               JsonMapper jsonMapper) {
        super(storedQuery, persistentEntity, conversionService);
        try {
            this.body = Objects.requireNonNull(jsonMapper.readValue(storedQuery.getQuery(), Argument.mapOf(String.class, Object.class)), "query body");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot parse the search query of " + storedQuery.getName() + ": " + storedQuery.getQuery(), e);
        }
    }

    /**
     * Builds the request body for an invocation, replacing every parameter placeholder with its bound value.
     *
     * @param invocationContext The invocation context
     * @param entity The entity, for entity-bound parameters
     * @param binder A binder converting values according to the bindings
     * @return The body
     */
    public Map<String, Object> bodyFor(@Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity, Binder binder) {
        CollectingBinder collecting = new CollectingBinder(binder);
        bindParameters(collecting, invocationContext, entity, null);
        return (Map<String, Object>) Objects.requireNonNull(replace(body, collecting.values));
    }

    @Nullable
    private Object replace(@Nullable Object node, List<Object> values) {
        if (node instanceof Map<?, ?> map) {
            Object placeholder = map.get(DocumentSearchQueryBuilder.QUERY_PARAMETER_PLACEHOLDER);
            if (placeholder instanceof Number index) {
                return resolvePlaceholder(map, index.intValue(), values);
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), replace(entry.getValue(), values));
            }
            return copy;
        }
        if (node instanceof Collection<?> collection) {
            List<Object> copy = new ArrayList<>(collection.size());
            for (Object item : collection) {
                Object replaced = replace(item, values);
                if (item instanceof Map<?, ?> map && map.containsKey(DocumentSearchQueryBuilder.QUERY_PARAMETER_PLACEHOLDER)
                    && replaced instanceof Collection<?> bound) {
                    // a parameter bound to a collection, such as the values of an In predicate, contributes its elements
                    copy.addAll(bound);
                } else {
                    copy.add(replaced);
                }
            }
            return copy;
        }
        return node;
    }

    /** The bound value of a placeholder, with its wildcard or similarity hint applied. */
    @Nullable
    private static Object resolvePlaceholder(Map<?, ?> placeholder, int index, List<Object> values) {
        Object value = normalize(index < 0 ? placeholder.get("value") : values.get(index));
        Object hint = placeholder.get(DocumentSearchQueryBuilder.WILDCARD_HINT);
        if (DocumentSearchQueryBuilder.COSINE_SIMILARITY_HINT.equals(hint)) {
            return value instanceof Number distance ? 1d - distance.doubleValue() : value;
        }
        return hint == null ? value : wildcard(value, hint.toString());
    }

    /**
     * Vectors and scores bind as plain JSON numbers.
     */
    @Nullable
    private static Object normalize(@Nullable Object value) {
        if (value instanceof Vector vector) {
            double[] doubles = vector.toDoubleArray();
            List<Object> list = new ArrayList<>(doubles.length);
            for (double d : doubles) {
                list.add(d);
            }
            return list;
        }
        if (value instanceof Score score) {
            return score.value();
        }
        return value;
    }

    private static Object wildcard(@Nullable Object value, String hint) {
        String text = value == null ? "" : escapeWildcard(value.toString());
        return switch (hint.replace("_ignore_case", "")) {
            case "starts_with" -> text + "*";
            case "ends_with" -> "*" + text;
            case "contains" -> "*" + text + "*";
            case "like" -> (value == null ? "" : value.toString()).replace("%", "*").replace("_", "?");
            default -> text;
        };
    }

    private static String escapeWildcard(String text) {
        return text.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?");
    }

    /**
     * Collects bound values in placeholder order, delegating conversion to the runtime binder.
     */
    private static final class CollectingBinder implements Binder {

        private final Binder delegate;
        private final List<Object> values = new ArrayList<>();

        CollectingBinder(Binder delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object autoPopulateRuntimeProperty(RuntimePersistentProperty<?> persistentProperty, @Nullable Object previousValue) {
            return delegate.autoPopulateRuntimeProperty(persistentProperty, previousValue);
        }

        @Override
        @Nullable
        public Object convert(@Nullable Object value, @Nullable RuntimePersistentProperty<?> property) {
            return delegate.convert(value, property);
        }

        @Override
        @Nullable
        public Object convert(@Nullable Class<?> converterClass, @Nullable Object value, @Nullable Argument<?> argument) {
            return delegate.convert(converterClass, value, argument);
        }

        @Override
        public void bindOne(QueryParameterBinding binding, @Nullable Object value) {
            values.add(value);
        }

        @Override
        public void bindMany(QueryParameterBinding binding, Collection<Object> values) {
            this.values.add(new ArrayList<>(values));
        }

        @Override
        public int currentIndex() {
            return values.size();
        }
    }
}
