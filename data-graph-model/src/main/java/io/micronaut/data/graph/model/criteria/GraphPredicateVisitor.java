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
package io.micronaut.data.graph.model.criteria;

import io.micronaut.core.annotation.Internal;

/**
 * A predicate visitor of a graph query builder, which renders the graph predicates.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public interface GraphPredicateVisitor {

    /**
     * Renders a predicate on the relationships of the root node.
     *
     * @param predicate The predicate
     */
    void visit(RelatedPredicate predicate);
}
