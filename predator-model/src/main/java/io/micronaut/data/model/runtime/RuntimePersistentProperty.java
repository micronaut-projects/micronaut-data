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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;

/**
 * A runtime representation of {@link PersistentProperty}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
class RuntimePersistentProperty implements PersistentProperty {

    private final RuntimePersistentEntity owner;
    private final BeanProperty<?, ?> property;

    /**
     * Default constructor.
     * @param owner The owner
     * @param property The property
     */
    RuntimePersistentProperty(RuntimePersistentEntity owner, BeanProperty<?, ?> property) {
        this.owner = owner;
        this.property = property;
    }

    @NonNull
    @Override
    public String getName() {
        return property.getName();
    }

    @NonNull
    @Override
    public String getTypeName() {
        return property.getType().getName();
    }

    @NonNull
    @Override
    public PersistentEntity getOwner() {
        return owner;
    }

    @Override
    public boolean isAssignable(@NonNull String type) {
        throw new UnsupportedOperationException("Use isAssignable(Class) instead");
    }

    @Override
    public boolean isAssignable(@NonNull Class<?> type) {
        return type.isAssignableFrom(getProperty().getType());
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return property.getAnnotationMetadata();
    }

    /**
     * @return The backing bean property
     */
    public BeanProperty<?, ?> getProperty() {
        return property;
    }

    @NonNull
    @Override
    public String getPersistedName() {
        return owner.getNamingStrategy().mappedName(this);
    }
}
