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
import io.micronaut.data.model.jpa.criteria.impl.expression.SubqueryExpression;
import io.micronaut.data.model.jpa.criteria.impl.selection.AliasedSelection;
import io.micronaut.data.model.jpa.criteria.impl.selection.CompoundSelection;

/**
 * The selection visitor.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public interface SelectionVisitor extends ExpressionVisitor {

    /**
     * Visit {@link AliasedSelection}.
     *
     * @param aliasedSelection The aliasedSelection
     */
    void visit(AliasedSelection<?> aliasedSelection);

    /**
     * Visit {@link CompoundSelection}.
     *
     * @param compoundSelection The compoundSelection
     */
    void visit(CompoundSelection<?> compoundSelection);

    @Override
    default void visit(IParameterExpression<?> parameterExpression) {
        throw new IllegalStateException("Parameter expression not supported in selection");
    }

    @Override
    default void visit(SubqueryExpression<?> subqueryExpression) {
        throw new IllegalStateException("Subquery not supported in selection");
    }
    
}
