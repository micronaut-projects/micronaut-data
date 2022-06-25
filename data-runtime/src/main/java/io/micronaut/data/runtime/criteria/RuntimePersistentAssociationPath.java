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
package io.micronaut.data.runtime.criteria;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.jpa.criteria.PersistentAssociationPath;
import io.micronaut.data.model.jpa.criteria.impl.SelectionVisitor;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import jakarta.persistence.criteria.Path;

import java.util.ArrayList;
import java.util.List;

/**
 * The runtime associated path.
 *
 * @param <Owner> The association owner type
 * @param <E>     The association entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
class RuntimePersistentAssociationPath<Owner, E> extends AbstractRuntimePersistentEntityJoinSupport<Owner, E>
        implements RuntimePersistentEntityPath<E>, PersistentAssociationPath<Owner, E> {

    private final Path<?> parentPath;
    private final RuntimeAssociation<Owner> association;
    private final List<Association> associations;
    private io.micronaut.data.annotation.Join.Type associationJoinType;
    @Nullable
    private String alias;

    RuntimePersistentAssociationPath(Path<?> parentPath,
                                     RuntimeAssociation<Owner> association,
                                     List<Association> associations,
                                     Join.Type associationJoinType,
                                     @Nullable String alias) {
        this.parentPath = parentPath;
        this.association = association;
        this.associations = associations;
        this.associationJoinType = associationJoinType;
        this.alias = alias;
    }

    @Override
    public Join.Type getAssociationJoinType() {
        return associationJoinType;
    }

    @Override
    public void setAssociationJoinType(Join.Type type) {
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
    public void accept(SelectionVisitor selectionVisitor) {
        selectionVisitor.visit(this);
    }

    @Override
    public Path<?> getParentPath() {
        return parentPath;
    }

    @Override
    public RuntimeAssociation<Owner> getProperty() {
        return association;
    }

    @Override
    public Association getAssociation() {
        return association;
    }

    @Override
    public List<Association> getAssociations() {
        return associations;
    }

    @Override
    public RuntimePersistentEntity<E> getPersistentEntity() {
        return (RuntimePersistentEntity) association.getAssociatedEntity();
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

    @Override
    public String toString() {
        return "RuntimePersistentAssociationPath{" +
                "parentPath=" + parentPath +
                ", association=" + association +
                ", associations=" + associations +
                '}';
    }
}
