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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.jpa.criteria.ExpressionType;
import io.micronaut.data.model.jpa.criteria.PersistentAssociationPath;
import io.micronaut.data.model.jpa.criteria.PersistentEntityFrom;
import io.micronaut.data.processor.model.SourceAssociation;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;

import java.util.ArrayList;
import java.util.List;

/**
 * The internal source implementation of {@link PersistentAssociationPath}.
 *
 * @param <Owner> The association entity type
 * @param <E>     The association entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
final class SourcePersistentAssociationPath<Owner, E> extends AbstractSourcePersistentEntityJoinSupport<Owner, E>
    implements SourcePersistentEntityPath<E>, SourcePersistentPropertyPath<E>, PersistentAssociationPath<Owner, E> {

    private final PersistentEntityFrom<?, Owner> parent;
    private final SourceAssociation association;
    private final List<Association> associations;
    private io.micronaut.data.annotation.Join.Type associationJoinType;
    @Nullable
    private String alias;

    SourcePersistentAssociationPath(PersistentEntityFrom<?, Owner> parent,
                                    SourceAssociation association,
                                    List<Association> associations,
                                    Join.Type associationJoinType,
                                    String alias,
                                    CriteriaBuilder criteriaBuilder) {
        super(criteriaBuilder);
        this.parent = parent;
        this.association = association;
        this.associations = associations;
        this.associationJoinType = associationJoinType;
        this.alias = alias;
    }

    @Override
    public ExpressionType<E> getExpressionType() {
        return SourcePersistentPropertyPath.super.getExpressionType();
    }

    @Override
    public PersistentEntityFrom<?, Owner> getParent() {
        return parent;
    }

    @Override
    public Join.Type getAssociationJoinType() {
        return associationJoinType;
    }

    @Override
    public void setAssociationJoinType(@Nullable Join.Type type) {
        this.associationJoinType = type;
    }

    @Override
    @Nullable
    public String getAlias() {
        return alias;
    }

    @Override
    public void setAlias(String alias) {
        this.alias = alias;
    }

    @Override
    public Path<?> getParentPath() {
        return parent;
    }

    @Override
    public SourceAssociation getProperty() {
        return association;
    }

    @Override
    public List<Association> getAssociations() {
        return associations;
    }

    @Override
    public Association getAssociation() {
        return association;
    }

    @Override
    public SourcePersistentEntity getPersistentEntity() {
        return association.getAssociatedEntity();
    }

    @Override
    protected List<Association> getCurrentPath() {
        return associated(getAssociations(), association);
    }

    private static List<Association> associated(List<Association> associations, Association association) {
        List<Association> newAssociations = new ArrayList<>(associations.size() + 1);
        newAssociations.addAll(associations);
        newAssociations.add(association);
        return newAssociations;
    }

}
