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
package io.micronaut.data.runtime.criteria.metamodel;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import jakarta.persistence.metamodel.CollectionAttribute;

@Internal
final class RuntimePersistentPropertyCollectionAttribute<T, E> extends RuntimePersistentPropertyPluralAttribute<T, java.util.Collection<E>, E>
    implements CollectionAttribute<T, E> {

    public RuntimePersistentPropertyCollectionAttribute(RuntimePersistentEntity<T> persistentEntity, RuntimePersistentProperty<T> persistentProperty) {
        super(persistentEntity, persistentProperty);
    }

    @Override
    public CollectionType getCollectionType() {
        return CollectionType.COLLECTION;
    }
}
