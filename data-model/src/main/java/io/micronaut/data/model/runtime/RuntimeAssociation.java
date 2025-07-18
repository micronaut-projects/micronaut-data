/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.model.runtime;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.exceptions.MappingException;
import io.micronaut.data.model.Association;

import java.util.Optional;

/**
 * A runtime representation of {@link Association}.
 *
 * @author graemerocher
 * @since 1.0
 * @param <T> the owning type
 */
public class RuntimeAssociation<T> extends RuntimePersistentProperty<T> implements Association {

    private final Relation.Kind kind;
    private final String aliasName;
    private final boolean isForeignKey;

    /**
     * Default constructor.
     * @param owner The owner
     * @param property The property
     * @param constructorArg Whether it is a constructor arg
     */
    RuntimeAssociation(RuntimePersistentEntity<T> owner, BeanProperty<T, Object> property, boolean constructorArg) {
        super(owner, property, constructorArg);
        this.kind = Association.super.getKind();
        this.aliasName = Association.super.getAliasName();
        this.isForeignKey = Association.super.isForeignKey();
    }

    @Override
    public boolean isForeignKey() {
        return isForeignKey;
    }

    @Override
    public String getAliasName() {
        return aliasName;
    }

    @NonNull
    @Override
    public Relation.Kind getKind() {
        return kind;
    }

    @Override
    public boolean isRequired() {
        return !isForeignKey() && super.isRequired();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<RuntimeAssociation<?>> getInverseSide() {
        return (Optional<RuntimeAssociation<?>>) Association.super.getInverseSide();
    }

    @NonNull
    @Override
    public RuntimePersistentEntity<?> getAssociatedEntity() {
        switch (getKind()) {
            case ONE_TO_MANY:
            case MANY_TO_MANY:
                Argument<?> typeArg = getProperty().asArgument().getFirstTypeVariable().orElse(null);
                if (typeArg  != null) {
                    //noinspection unchecked
                    return getOwner().getEntity((Class<T>) typeArg.getType());
                } else {
                    throw new MappingException("Collection association [" + getName() + "] of entity [" + getOwner().getName() + "] does not specify a generic type argument");
                }
            default:
                //noinspection unchecked
                return getOwner().getEntity((Class<T>) getProperty().getType());
        }
    }
}
