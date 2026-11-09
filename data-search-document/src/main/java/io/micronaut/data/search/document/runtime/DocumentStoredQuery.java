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
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.runtime.operations.internal.query.DefaultBindableParametersStoredQuery;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.model.vector.search.Score;
import io.micronaut.data.search.document.query.DocumentSearchQueryBuilder;
import io.micronaut.data.search.document.query.SearchJson;
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
 * invocation. Bound values other than strings, numbers and booleans are converted with the
 * {@link JsonMapper} that writes the documents, so a date, a temporal or an enum with a custom
 * serialised name is compared in the form it is stored in.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @since 5.3.0
 */
@Internal
public final class DocumentStoredQuery<E, R> extends DefaultBindableParametersStoredQuery<E, R> {

    private static final String LIKE_WITH_ESCAPE = "like:";
    private static final String IGNORE_CASE = "_ignore_case";

    private final Map<String, Object> body;
    private final boolean countProjection;
    private final JsonMapper jsonMapper;

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
        this.jsonMapper = jsonMapper;
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
            // a named parameter is a bound value, converted like a placeholder after its vector or score is normalized
            return toJsonValue(normalize(named.get(string)));
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

    /**
     * The value a placeholder stands for, a bound parameter or the literal it carries when the index is
     * negative, with its wildcard or similarity hint applied.
     */
    @Nullable
    private Object resolvePlaceholder(Map<?, ?> placeholder, int index, List<Object> values) {
        // a literal comes from the parsed body and is JSON already; vectors and scores are normalized to
        // numbers before the other bound values are converted with the JSON mapper
        Object value = index < 0 ? normalize(placeholder.get("value")) : toJsonValue(normalize(values.get(index)));
        Object hint = placeholder.get(DocumentSearchQueryBuilder.WILDCARD_HINT);
        if (DocumentSearchQueryBuilder.COSINE_SIMILARITY_HINT.equals(hint)) {
            return value instanceof Number distance ? 1d - distance.doubleValue() : value;
        }
        if (hint == null) {
            return value;
        }
        String kind = hint.toString();
        if (placeholder.get(DocumentSearchQueryBuilder.ESCAPE_HINT) instanceof Map<?, ?> escapePlaceholder
            && escapePlaceholder.get(DocumentSearchQueryBuilder.QUERY_PARAMETER_PLACEHOLDER) instanceof Number escapeIndex) {
            // the escape character of a LIKE bound at runtime travels as a parameter of its own
            Object escape = values.get(escapeIndex.intValue());
            if (escape != null && !escape.toString().isEmpty()) {
                kind = LIKE_WITH_ESCAPE + escape.toString().charAt(0) + (kind.contains(IGNORE_CASE) ? IGNORE_CASE : "");
            }
        }
        return wildcard(value, kind);
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
        if (value != null && value.getClass().isArray()) {
            // an array bound to an In predicate is spliced into the list like a collection
            return SearchJson.arrayToList(value);
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

    /**
     * Converts a bound value to the JSON form the {@link JsonMapper} writes it in within a document;
     * an array or a collection, as bound to an In predicate, becomes a list of converted elements.
     */
    @Nullable
    private Object toJsonValue(@Nullable Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value.getClass().isArray()) {
            return toJsonValue(SearchJson.arrayToList(value));
        }
        if (value instanceof Collection<?> collection) {
            List<Object> converted = new ArrayList<>(collection.size());
            for (Object item : collection) {
                converted.add(toJsonValue(item));
            }
            return converted;
        }
        try {
            return jsonMapper.readValue(jsonMapper.writeValueAsBytes(value), Argument.OBJECT_ARGUMENT);
        } catch (IOException e) {
            throw new DataAccessException("Cannot write the query parameter " + value + " as JSON: " + e.getMessage(), e);
        }
    }

    private static Object wildcard(@Nullable Object value, String hint) {
        String kind = hint.replace(IGNORE_CASE, "");
        if (kind.startsWith("like")) {
            // like, or like:<escape character>
            Character escape = kind.length() > LIKE_WITH_ESCAPE.length() ? kind.charAt(LIKE_WITH_ESCAPE.length()) : null;
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
