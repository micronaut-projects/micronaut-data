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
package io.micronaut.data.nitrite.model.query.builder;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Sort;

/**
 * One key of an {@code ORDER BY}, in the form Nitrite reads it.
 *
 * <p>A key is either a stored field or a computed expression. Nitrite can only order by a stored
 * field, and orders nulls first before reversing for a descending key - so it expresses exactly
 * ascending/nulls-first and descending/nulls-last and nothing else. A key this record can hold but
 * Nitrite cannot is ordered by the adapter instead; see
 * {@link io.micronaut.data.nitrite.runtime.read.NitriteSortPlan}.
 *
 * @param field the persisted field name, or {@code null} when the key is an expression
 * @param expression the {@code $expr} operand tree evaluated per document, or {@code null} when the
 *                   key is a stored field
 * @param ascending whether the key orders ascending
 * @param nullOrdering where nulls of this key are placed, {@link Sort.Order.NullOrdering#NONE} to
 *                    leave the placement to Nitrite
 * @since 5.2.0
 */
@Internal
public record NitriteSortKey(
    @Nullable String field,
    @Nullable Object expression,
    boolean ascending,
    Sort.Order.NullOrdering nullOrdering) {

    /**
     * A key ordering by a stored field.
     *
     * @param field the persisted field name
     * @param ascending whether the key orders ascending
     * @param nullOrdering where nulls of this key are placed
     * @return the key
     */
    public static NitriteSortKey ofField(String field, boolean ascending, Sort.Order.NullOrdering nullOrdering) {
        return new NitriteSortKey(field, null, ascending, nullOrdering);
    }

    /**
     * A key ordering by a value computed per document.
     *
     * @param expression the {@code $expr} operand tree
     * @param ascending whether the key orders ascending
     * @param nullOrdering where nulls of this key are placed
     * @return the key
     */
    public static NitriteSortKey ofExpression(Object expression, boolean ascending, Sort.Order.NullOrdering nullOrdering) {
        return new NitriteSortKey(null, expression, ascending, nullOrdering);
    }

    /**
     * Whether this key is computed per document rather than read from a stored field.
     *
     * @return whether this key is computed rather than read from a stored field
     */
    public boolean isExpression() {
        return expression != null;
    }

    /**
     * Whether Nitrite can order by this key on its own. Nitrite reads stored fields only, and puts
     * nulls first on an ascending key and last on a descending one; any other placement has to be
     * ordered by the adapter.
     *
     * @return whether the key can be handed to Nitrite's {@code FindOptions}
     */
    public boolean isNativelySortable() {
        if (isExpression()) {
            return false;
        }
        return switch (nullOrdering) {
            case NONE -> true;
            case FIRST -> ascending;
            case LAST -> !ascending;
        };
    }
}
