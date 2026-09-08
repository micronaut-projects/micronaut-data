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
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.nitrite.model.query.builder.NitriteSortKey;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterBuilder;
import io.micronaut.data.nitrite.runtime.query.ast.NitriteFilterAST;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.FindOptions;
import org.dizitart.no2.common.SortOrder;
import org.dizitart.no2.common.DBNull;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * How the {@code ORDER BY} of one read is carried out.
 *
 * <p>Nitrite orders by a stored field only, and places nulls first before reversing the whole
 * comparison for a descending key - so a find expresses ascending/nulls-first and
 * descending/nulls-last, and nothing else. A sort that stays inside that goes to the find, where an
 * index can serve it. A sort that does not - one ordering by a computed expression, or asking for
 * nulls at the end Nitrite does not put them - is carried out here instead, over the documents the
 * find returns. Skip and limit then have to come off the find as well, because they would otherwise
 * cut the result before it is ordered.
 *
 * @since 5.2.0
 */
@Internal
public final class NitriteSortPlan {

    private final List<Key> keys;
    private final boolean nativelySortable;
    private long skip;
    private long limit;

    private NitriteSortPlan(List<Key> keys, boolean nativelySortable) {
        this.keys = keys;
        this.nativelySortable = nativelySortable;
    }

    /**
     * Builds the plan for a sort.
     *
     * @param sortKeys the sort keys, in order of precedence
     * @param entity the root entity
     * @param entityMapper resolves a property name to the field the document stores it under
     * @param filterBuilder compiles a computed key into the shared {@code $expr} evaluator
     * @param params positional parameters an expression key may reference
     * @param namedParameters named parameters an expression key may reference
     * @return the plan, sorting nothing when there are no keys
     */
    public static NitriteSortPlan build(List<NitriteSortKey> sortKeys,
                                        @Nullable RuntimePersistentEntity<?> entity,
                                        NitriteEntityMapper entityMapper,
                                        NitriteFilterBuilder filterBuilder,
                                        Object[] params,
                                        Map<String, Object> namedParameters) {
        if (sortKeys.isEmpty()) {
            return new NitriteSortPlan(List.of(), true);
        }
        boolean nativelySortable = sortKeys.stream().allMatch(NitriteSortKey::isNativelySortable);
        List<Key> keys = new ArrayList<>(sortKeys.size());
        for (NitriteSortKey sortKey : sortKeys) {
            Function<Document, Object> accessor;
            String field = null;
            if (sortKey.isExpression()) {
                RuntimePersistentEntity<?> rootEntity = Objects.requireNonNull(entity,
                    "An expression sort requires a root entity");
                Object expressionTree = Objects.requireNonNull(sortKey.expression(),
                    "An expression sort must have an expression");
                NitriteFilterAST.ExprValueNode expression =
                    filterBuilder.compileSortExpression(rootEntity, expressionTree);
                accessor = document -> expression.evaluate(document, params, namedParameters);
            } else {
                String sortField = Objects.requireNonNull(sortKey.field(),
                    "A stored sort key must have a field");
                field = entityMapper.normalizeFieldName(sortField, entity);
                String persistedName = field;
                accessor = document -> document.get(persistedName);
            }
            keys.add(new Key(field, accessor, sortKey.ascending(), sortKey.nullOrdering()));
        }
        return new NitriteSortPlan(keys, nativelySortable);
    }

    /**
     * Whether every key of this sort is one Nitrite orders by on its own.
     *
     * @return whether the find can carry this sort itself
     */
    public boolean isNativelySortable() {
        return nativelySortable;
    }

    /**
     * Puts this sort on the find, for a sort the find can carry.
     *
     * @param options the find options
     */
    public void applyNativeOrder(FindOptions options) {
        for (Key key : keys) {
            options.thenOrderBy(key.field(), key.ascending() ? SortOrder.Ascending : SortOrder.Descending);
        }
    }

    /**
     * Takes the row window off the find, for a sort the find cannot carry: the window would
     * otherwise cut the result before {@link #apply} orders it. The caller leaves the ordering off
     * the find by not calling {@link #applyNativeOrder}.
     *
     * @param options the find options, left without a window
     */
    public void withholdWindow(FindOptions options) {
        Long optionsSkip = options.skip();
        Long optionsLimit = options.limit();
        skip = optionsSkip == null ? 0 : optionsSkip;
        limit = optionsLimit == null ? -1 : optionsLimit;
        options.skip((Long) null);
        options.limit((Long) null);
    }

    /**
     * Orders the documents of a find this plan was withheld from, then applies the withheld window.
     *
     * @param documents the documents the find returned
     * @param collator the collator the find would have compared strings with
     * @return the ordered and windowed documents
     */
    public List<Document> apply(List<Document> documents, @Nullable Collator collator) {
        List<Document> ordered = new ArrayList<>(documents);
        ordered.sort(comparator(collator));
        int from = (int) Math.min(skip, ordered.size());
        int to = limit < 0 ? ordered.size() : (int) Math.min(from + limit, ordered.size());
        return ordered.subList(from, to);
    }

    private Comparator<Document> comparator(@Nullable Collator collator) {
        return (left, right) -> {
            for (Key key : keys) {
                int result = key.compare(left, right, collator);
                if (result != 0) {
                    return result;
                }
            }
            return 0;
        };
    }

    private record Key(@Nullable String field,
                       Function<Document, Object> accessor,
                       boolean ascending,
                       Sort.Order.NullOrdering nullOrdering) {

        int compare(Document left, Document right, @Nullable Collator collator) {
            Object leftValue = accessor.apply(left);
            Object rightValue = accessor.apply(right);
            boolean leftNull = isNull(leftValue);
            boolean rightNull = isNull(rightValue);
            if (leftNull && rightNull) {
                return 0;
            }
            if (leftNull || rightNull) {
                return compareNull(leftNull);
            }
            int result = compareValues(leftValue, rightValue, collator);
            return ascending ? result : -result;
        }

        /**
         * Places a null against a non-null. An asked-for placement is absolute, so the direction
         * does not reverse it; without one, nulls go first and the direction reverses that, which
         * is what the find would have done.
         */
        private int compareNull(boolean leftNull) {
            return switch (nullOrdering) {
                case FIRST -> leftNull ? -1 : 1;
                case LAST -> leftNull ? 1 : -1;
                case NONE -> (leftNull ? -1 : 1) * (ascending ? 1 : -1);
            };
        }

        private static boolean isNull(@Nullable Object value) {
            return value == null || value instanceof DBNull;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static int compareValues(Object left, Object right, @Nullable Collator collator) {
            if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
                return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
            }
            if (left instanceof String && right instanceof String && collator != null) {
                return collator.compare(left, right);
            }
            if (left instanceof Comparable comparable && left.getClass().isInstance(right)) {
                return comparable.compareTo(right);
            }
            return left.toString().compareTo(right.toString());
        }
    }
}
