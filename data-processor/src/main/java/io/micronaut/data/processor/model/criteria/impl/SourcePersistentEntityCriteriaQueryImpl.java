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
package io.micronaut.data.processor.model.criteria.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.jpa.criteria.ISelection;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaQuery;
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
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaQuery;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.ast.ClassElement;
import jakarta.persistence.criteria.Predicate;

import java.util.function.Function;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireProperty;

/**
 * The internal source implementation of {@link SourcePersistentEntityCriteriaQuery}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
final class SourcePersistentEntityCriteriaQueryImpl<T> extends AbstractPersistentEntityCriteriaQuery<T>
        implements SourcePersistentEntityCriteriaQuery<T> {

    private final Function<ClassElement, SourcePersistentEntity> entityResolver;

    public SourcePersistentEntityCriteriaQueryImpl(Function<ClassElement, SourcePersistentEntity> entityResolver) {
        super((Class<T>) Object.class);
        this.entityResolver = entityResolver;
    }

    @Override
    public <X> PersistentEntityRoot<X> from(ClassElement entityClassElement) {
        return from(new SourcePersistentEntity(entityClassElement, entityResolver));
    }

    @Override
    public <X> PersistentEntityRoot<X> from(Class<X> entityClass) {
        throw new IllegalStateException("Unsupported operation");
    }

    @Override
    public <X> PersistentEntityRoot<X> from(PersistentEntity persistentEntity) {
        if (entityRoot != null) {
            throw new IllegalStateException("The root entity is already specified!");
        }
        SourcePersistentEntityRoot<X> newEntityRoot = new SourcePersistentEntityRoot<>((SourcePersistentEntity) persistentEntity);
        entityRoot = newEntityRoot;
        return newEntityRoot;
    }

    @Override
    public String getQueryResultTypeName() {
        if (selection instanceof ISelection) {
            String[] result = new String[1];
            ((SelectionVisitable) selection).accept(new SelectionVisitor() {
                @Override
                public void visit(Predicate predicate) {
                }

                @Override
                public void visit(PersistentPropertyPath<?> persistentPropertyPath) {
                    if (persistentPropertyPath.getProperty() instanceof SourceAssociation sourceAssociation) {
                        result[0] = sourceAssociation.getAssociatedEntity().getType().getName();
                    } else {
                        result[0] = ((SourcePersistentPropertyPath) persistentPropertyPath).getProperty().getType().getName();
                    }
                }

                @Override
                public void visit(AliasedSelection<?> aliasedSelection) {
                    ((SelectionVisitable) aliasedSelection.getSelection()).accept(this);
                }

                @Override
                public void visit(PersistentEntityRoot<?> entityRoot) {
                    result[0] = ((SourcePersistentEntity) entityRoot.getPersistentEntity()).getType().getName();
                }

                @Override
                public void visit(AggregateExpression<?, ?> aggregateExpression) {
                    switch (aggregateExpression.getType()) {
                        case COUNT:
                        case COUNT_DISTINCT:
                            result[0] = Long.class.getName();
                            break;
                        case MAX:
                        case MIN:
                            result[0] = requireProperty(aggregateExpression.getExpression()).getProperty().getTypeName();
                            break;
                        case SUM:
                        case AVG:
                            ClassElement type = ((SourcePersistentProperty) requireProperty(aggregateExpression.getExpression()).getProperty()).getType();
                            if (aggregateExpression.getExpressionType() != null) {
                                result[0] = aggregateExpression.getExpressionType().getName();
                            }
                            if (TypeUtils.isNumber(type)) {
                                result[0] = Number.class.getName();
                            } else {
                                result[0] = type.getName();
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
                    result[0] = literalExpression.getValue().getClass().getName();
                }

                @Override
                public void visit(IdExpression<?, ?> idExpression) {
                    SourcePersistentEntity persistentEntity = (SourcePersistentEntity) idExpression.getRoot().getPersistentEntity();
                    if (persistentEntity.hasCompositeIdentity()) {
                        throw new IllegalStateException("IdClass is unknown!");
                    }
                    result[0] = persistentEntity.getIdentity().getType().getName();
                }
            });
            return result[0];
        }
        return null;
    }
}
