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
package io.micronaut.data.graph.model.query.builder;

import io.micronaut.core.annotation.Internal;
import jakarta.persistence.criteria.Nulls;
import org.jspecify.annotations.Nullable;

/**
 * A rendered {@code ORDER BY} item of a Cypher query.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class OrderItem {
    final String expression;
    final boolean ascending;
    final Nulls nulls;
    final boolean onAssociation;
    @Nullable String alias;

    OrderItem(String expression, boolean ascending, Nulls nulls, boolean onAssociation) {
        this.expression = expression;
        this.ascending = ascending;
        this.nulls = nulls;
        this.onAssociation = onAssociation;
    }
}
