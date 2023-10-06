/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.processor.model.criteria.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.IdExpression;
import io.micronaut.data.model.jpa.criteria.impl.LiteralExpression;
import io.micronaut.data.model.jpa.criteria.impl.SelectionVisitable;
import io.micronaut.data.model.jpa.criteria.impl.SelectionVisitor;
import io.micronaut.data.model.jpa.criteria.impl.selection.AggregateExpression;
import io.micronaut.data.model.jpa.criteria.impl.selection.AliasedSelection;
import io.micronaut.data.model.jpa.criteria.impl.selection.CompoundSelection;
import io.micronaut.data.processor.model.SourceAssociation;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.ast.ClassElement;
import jakarta.persistence.criteria.Predicate;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireProperty;

/**
 * The query result type analyzer.
 *
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
final class QueryResultAnalyzer implements SelectionVisitor {
    private String queryResultTypeName;

    public String getQueryResultTypeName() {
        return queryResultTypeName;
    }

    @Override
    public void visit(Predicate predicate) {
    }

    @Override
    public void visit(PersistentPropertyPath<?> persistentPropertyPath) {
        if (persistentPropertyPath.getProperty() instanceof SourceAssociation sourceAssociation) {
            queryResultTypeName = sourceAssociation.getAssociatedEntity().getType().getName();
        } else {
            queryResultTypeName = ((SourcePersistentPropertyPath) persistentPropertyPath).getProperty().getType().getName();
        }
    }

    @Override
    public void visit(AliasedSelection<?> aliasedSelection) {
        ((SelectionVisitable) aliasedSelection.getSelection()).accept(this);
    }

    @Override
    public void visit(PersistentEntityRoot<?> entityRoot) {
        queryResultTypeName = ((SourcePersistentEntity) entityRoot.getPersistentEntity()).getType().getName();
    }

    @Override
    public void visit(AggregateExpression<?, ?> aggregateExpression) {
        switch (aggregateExpression.getType()) {
            case COUNT:
            case COUNT_DISTINCT:
                queryResultTypeName = Long.class.getName();
                break;
            case MAX:
            case MIN:
                queryResultTypeName = requireProperty(aggregateExpression.getExpression()).getProperty().getTypeName();
                break;
            case SUM:
            case AVG:
                ClassElement type = ((SourcePersistentProperty) requireProperty(aggregateExpression.getExpression()).getProperty()).getType();
                if (aggregateExpression.getExpressionType() != null) {
                    queryResultTypeName = aggregateExpression.getExpressionType().getName();
                }
                if (TypeUtils.isNumber(type)) {
                    queryResultTypeName = Number.class.getName();
                } else {
                    queryResultTypeName = type.getName();
                }
                break;
            default:
        }
    }

    @Override
    public void visit(CompoundSelection<?> compoundSelection) {
        if (compoundSelection.getCompoundSelectionItems().size() == 1) {
            // Multiple selection shouldn't result in one type
            compoundSelection.getCompoundSelectionItems().forEach(s -> ((SelectionVisitable) s).accept(this));
        }
    }

    @Override
    public void visit(LiteralExpression<?> literalExpression) {
        queryResultTypeName = literalExpression.getValue().getClass().getName();
    }

    @Override
    public void visit(IdExpression<?, ?> idExpression) {
        SourcePersistentEntity persistentEntity = (SourcePersistentEntity) idExpression.getRoot().getPersistentEntity();
        if (persistentEntity.hasCompositeIdentity()) {
            throw new IllegalStateException("IdClass is unknown!");
        }
        queryResultTypeName = persistentEntity.getIdentity().getType().getName();
    }
}
