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
package io.micronaut.data.runtime.intercept;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;

/**
 * Checks whether entity identity values are present.
 */
@Internal
interface EntityIdentityPresenceChecker {

    /**
     * Whether the entity has an identity value.
     *
     * @param persistentEntity The persistent entity
     * @param entity           The entity instance
     * @return True if the identity value is present
     */
    boolean hasIdentity(RuntimePersistentEntity<Object> persistentEntity, Object entity);

    /**
     * Whether every identity property is generated.
     *
     * @param persistentEntity The persistent entity
     * @return True if the identity is generated
     */
    boolean hasGeneratedIdentity(RuntimePersistentEntity<Object> persistentEntity);

    /**
     * Whether the entity has a non-generated identity value that is not negative.
     *
     * @param persistentEntity The persistent entity
     * @param entity           The entity instance
     * @return True if the identity value is non-generated and not negative
     */
    boolean hasNonGeneratedNonNegativeIdentity(RuntimePersistentEntity<Object> persistentEntity, Object entity);
}
