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
package io.micronaut.data.processor.model.criteria;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.inject.ast.ClassElement;

/**
 * The source persistent entity extension of {@link PersistentEntityCriteriaDelete}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public interface SourcePersistentEntityCriteriaDelete<T> extends PersistentEntityCriteriaDelete<T> {

    /**
     * Creates a {@link PersistentEntityRoot} from class element representing the entity.
     *
     * @param entityClassElement The entity class element
     * @return new root
     */
    @NonNull
    PersistentEntityRoot<T> from(@NonNull ClassElement entityClassElement);

}
