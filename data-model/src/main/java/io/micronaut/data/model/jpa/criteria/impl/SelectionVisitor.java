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
package io.micronaut.data.model.jpa.criteria.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.selection.AggregateExpression;
import io.micronaut.data.model.jpa.criteria.impl.selection.AliasedSelection;
import io.micronaut.data.model.jpa.criteria.impl.selection.CompoundSelection;
import jakarta.persistence.criteria.Predicate;

/**
 * The selection visitor.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public interface SelectionVisitor {

    /**
     * Visit {@link Predicate}.
     *
     * @param predicate The predicate
     */
    void visit(Predicate predicate);

    /**
     * Visit {@link PersistentPropertyPath}.
     *
     * @param persistentPropertyPath The persistentPropertyPath
     */
    void visit(PersistentPropertyPath<?> persistentPropertyPath);

    /**
     * Visit {@link AliasedSelection}.
     *
     * @param aliasedSelection The aliasedSelection
     */
    void visit(AliasedSelection<?> aliasedSelection);

    /**
     * Visit {@link PersistentEntityRoot}.
     *
     * @param entityRoot The entityRoot
     */
    void visit(PersistentEntityRoot<?> entityRoot);

    /**
     * Visit {@link CompoundSelection}.
     *
     * @param compoundSelection The compoundSelection
     */
    void visit(CompoundSelection<?> compoundSelection);

    /**
     * Visit {@link LiteralExpression}.
     *
     * @param literalExpression The literalExpression
     */
    void visit(LiteralExpression<?> literalExpression);

    /**
     * Visit {@link AggregateExpression}.
     *
     * @param aggregateExpression The aggregateExpression
     */
    void visit(AggregateExpression<?, ?> aggregateExpression);

    /**
     * Visit {@link IdExpression}.
     *
     * @param idExpression The idExpression
     */
    void visit(IdExpression<?, ?> idExpression);

}
