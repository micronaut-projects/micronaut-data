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
    private final boolean countProjection;

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
            Map<String, Object> parsed = Objects.requireNonNull(jsonMapper.readValue(storedQuery.getQuery(), Argument.mapOf(String.class, Object.class)), "query body");
            this.countProjection = Boolean.TRUE.equals(parsed.remove(DocumentSearchQueryBuilder.COUNT_MARKER));
            this.body = parsed;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot parse the search query of " + storedQuery.getName() + ": " + storedQuery.getQuery(), e);
        }
    }

    @Override
    public boolean isCount() {
        return countProjection || super.isCount();
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
        Map<String, Object> named = Map.of();
        if (isRawQuery()) {
            // a @Query body in the store's own DSL names its parameters as ":name" string values
            named = new LinkedHashMap<>();
            List<QueryParameterBinding> bindings = getQueryBindings();
            for (int i = 0; i < bindings.size() && i < collecting.values.size(); i++) {
                String name = parameterName(bindings.get(i), invocationContext);
                if (name != null) {
                    named.put(":" + name, collecting.values.get(i));
                }
            }
        }
        return (Map<String, Object>) Objects.requireNonNull(replace(body, collecting.values, named));
    }

    @Nullable
    private static String parameterName(QueryParameterBinding binding, @Nullable InvocationContext<?, ?> invocationContext) {
        if (binding.getName() != null) {
            return binding.getName();
        }
        int index = binding.getParameterIndex();
        if (invocationContext != null && index >= 0 && index < invocationContext.getArguments().length) {
            return invocationContext.getArguments()[index].getName();
        }
        return null;
    }

    @Nullable
    private Object replace(@Nullable Object node, List<Object> values, Map<String, Object> named) {
        if (node instanceof String string && named.containsKey(string)) {
            return normalize(named.get(string));
        }
        if (node instanceof Map<?, ?> map && !map.containsKey(DocumentSearchQueryBuilder.QUERY_PARAMETER_PLACEHOLDER)) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), replace(entry.getValue(), values, named));
            }
            return copy;
        }
        if (node instanceof Collection<?> collection && !named.isEmpty()) {
            List<Object> copy = new ArrayList<>(collection.size());
            for (Object item : collection) {
                copy.add(replace(item, values, named));
            }
            return copy;
        }
        return replace(node, values);
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
            return replaceItems(collection, values);
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

    /** Replaces the items of a list; a parameter bound to a collection, such as the values of an In predicate, contributes its elements. */
    private List<Object> replaceItems(Collection<?> collection, List<Object> values) {
        List<Object> copy = new ArrayList<>(collection.size());
        for (Object item : collection) {
            Object replaced = replace(item, values);
            boolean placeholder = item instanceof Map<?, ?> map && map.containsKey(DocumentSearchQueryBuilder.QUERY_PARAMETER_PLACEHOLDER);
            if (placeholder && replaced instanceof Collection<?> bound) {
                copy.addAll(bound);
            } else {
                copy.add(replaced);
            }
        }
        return copy;
    }

    private static Object wildcard(@Nullable Object value, String hint) {
        String kind = hint.replace("_ignore_case", "");
        if (kind.startsWith("like")) {
            // like, or like:<escape character>
            Character escape = kind.length() > "like:".length() ? kind.charAt("like:".length()) : null;
            return likeToWildcard(value == null ? "" : value.toString(), escape);
        }
        String text = value == null ? "" : escapeWildcard(value.toString());
        return switch (kind) {
            case "starts_with" -> text + "*";
            case "ends_with" -> "*" + text;
            case "contains" -> "*" + text + "*";
            default -> text;
        };
    }

    /**
     * Converts a SQL LIKE pattern into a wildcard pattern: {@code %} and {@code _} become {@code *} and
     * {@code ?}, the wildcard characters of the pattern's literal text are escaped, and the escape
     * character makes the next character literal.
     */
    static String likeToWildcard(String pattern, @Nullable Character escape) {
        StringBuilder sb = new StringBuilder(pattern.length() + 8);
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (escape != null && c == escape && i + 1 < pattern.length()) {
                sb.append(escapeWildcard(String.valueOf(pattern.charAt(++i))));
            } else if (c == '%') {
                sb.append('*');
            } else if (c == '_') {
                sb.append('?');
            } else {
                sb.append(escapeWildcard(String.valueOf(c)));
            }
        }
        return sb.toString();
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
