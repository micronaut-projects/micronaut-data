/*
 * Copyright 2017-2019 original authors
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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;

/**
 * A runtime representation of {@link Association}.
 *
 * @author graemerocher
 * @since 1.0
 * @param <T> the owning type
 */
class RuntimeAssociation<T> extends RuntimePersistentProperty<T> implements Association {

    private final Relation.Kind kind;
    private final String aliasName;

    /**
     * Default constructor.
     * @param owner The owner
     * @param property The property
     */
    RuntimeAssociation(RuntimePersistentEntity<T> owner, BeanProperty<T, ?> property) {
        super(owner, property);
        this.kind = Association.super.getKind();
        this.aliasName = Association.super.getAliasName();
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

    @Nullable
    @Override
    public PersistentEntity getAssociatedEntity() {
        switch (getKind()) {
            case ONE_TO_MANY:
            case MANY_TO_MANY:
                Argument<?> typeArg = getProperty().asArgument().getFirstTypeVariable().orElse(null);
                if (typeArg  != null) {
                    return PersistentEntity.of(typeArg.getType());
                }
            default:
                return PersistentEntity.of(getProperty().getType());
        }
    }
}
