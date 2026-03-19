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
package io.micronaut.data.model.jpa.criteria;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.jpa.criteria.impl.ExpressionVisitor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Experimental
public interface PersistentAssociationAttributePath<OwnerType, AssociatedEntityType> extends PersistentPropertyPath<AssociatedEntityType> {

    @Override
    Association getProperty();

    Association getAssociation();

    @Override
    default io.micronaut.data.model.PersistentAssociationPath getPropertyPath() {
        return new io.micronaut.data.model.PersistentAssociationPath(getAssociations(), getProperty());
    }

    io.micronaut.data.annotation.Join.@Nullable Type getAssociationJoinType();

    void setAssociationJoinType(io.micronaut.data.annotation.Join.Type type);

    void setAlias(String alias);

    default List<Association> asPath() {
        List<Association> associations = getAssociations();
        List<Association> newAssociations = new ArrayList<>(associations.size() + 1);
        newAssociations.addAll(associations);
        newAssociations.add(getAssociation());
        return newAssociations;
    }

    Collection<? extends PersistentAssociationAttributePath<AssociatedEntityType, ?>> getPersistentJoins();

    @Override
    default void visitExpression(ExpressionVisitor expressionVisitor) {
        expressionVisitor.visit(this);
    }
}
