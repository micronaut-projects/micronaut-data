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
package io.micronaut.data.model.jpa.criteria;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.jpa.criteria.impl.ExpressionVisitor;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Bindable;
import jakarta.persistence.metamodel.MapAttribute;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;

/**
 * The persistent entity association path.
 *
 * @param <OwnerType>            The association owner type
 * @param <AssociatedEntityType> The association entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public interface PersistentAssociationPath<OwnerType, AssociatedEntityType> extends PersistentEntityJoin<OwnerType, AssociatedEntityType>,
        PersistentPropertyPath<AssociatedEntityType> {

    @Override
    Association getProperty();

    Association getAssociation();

    @Override
    default io.micronaut.data.model.PersistentAssociationPath getPropertyPath() {
        return new io.micronaut.data.model.PersistentAssociationPath(getAssociations(), getProperty());
    }

    /**
     * @return The join type
     */
    io.micronaut.data.annotation.Join. @Nullable Type getAssociationJoinType();

    @Override
    @Nullable
    default JoinType getJoinType() {
        return null;
    }

    /**
     * Set join type.
     *
     * @param type The join type
     */
    void setAssociationJoinType(io.micronaut.data.annotation.Join.Type type);

    /**
     * Set join alias.
     *
     * @param alias The alias
     */
    void setAlias(String alias);

    default List<Association> asPath() {
        List<Association> associations = getAssociations();
        List<Association> newAssociations = new ArrayList<>(associations.size() + 1);
        newAssociations.addAll(associations);
        newAssociations.add(getAssociation());
        return newAssociations;
    }

    @Override

    default Join<OwnerType, AssociatedEntityType> on(Expression<Boolean> restriction) {
        throw notSupportedOperation();
    }

    @Override

    default Join<OwnerType, AssociatedEntityType> on(Predicate... restrictions) {
        throw notSupportedOperation();
    }

    @Override
    @Nullable
    default Predicate getOn() {
        throw notSupportedOperation();
    }

    @Override

    default Attribute<? super OwnerType, ?> getAttribute() {
        throw notSupportedOperation();
    }

    @Override

    default Bindable<AssociatedEntityType> getModel() {
        throw notSupportedOperation();
    }

    @Override
    default void visitExpression(ExpressionVisitor expressionVisitor) {
        expressionVisitor.visit(this);
    }

    @Override
    <Y> PersistentPropertyPath<Y> get(String attributeName);

    @Override
    default <Y> PersistentPropertyPath<Y> get(SingularAttribute<? super AssociatedEntityType, Y> attribute) {
        return get(attribute.getName());
    }

    @Override
    default <E, C extends Collection<E>> Expression<C> get(PluralAttribute<? super AssociatedEntityType, C, E> collection) {
        return get(collection.getName());
    }

    @Override
    default <K, V, M extends Map<K, V>> Expression<M> get(MapAttribute<? super AssociatedEntityType, K, V> map) {
        return get(map.getName());
    }

    @Override
    <X, Y> PersistentAssociationPath<X, Y> join(String attributeName);

    @Override
    <X, Y> PersistentAssociationPath<X, Y> join(String attributeName, io.micronaut.data.annotation.Join. @Nullable Type joinType);

    @Override
    <X, Y> PersistentAssociationPath<X, Y> join(String attributeName, io.micronaut.data.annotation.Join. @Nullable Type joinType, @Nullable String alias);

    @Override
    <X, Y> PersistentAssociationPath<X, Y> join(String attributeName, JoinType jt);

    @Override
    <Y> PersistentAssociationPath<AssociatedEntityType, Y> join(SingularAttribute<? super AssociatedEntityType, Y> attribute, JoinType jt);

    @Override
    <Y> PersistentAssociationPath<AssociatedEntityType, Y> join(SingularAttribute<? super AssociatedEntityType, Y> attribute);

    @Override
    <Y> PersistentAssociationPath<AssociatedEntityType, Y> join(SingularAttribute<? super AssociatedEntityType, Y> attribute, io.micronaut.data.annotation.Join.Type jt);
}
