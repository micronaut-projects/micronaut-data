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
package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.CompositeIdCollectionParent;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@NitriteRepository
public interface CompositeIdCollectionParentRepository
    extends CrudRepository<CompositeIdCollectionParent, CompositeIdCollectionParent> {

    @Join("children")
    Optional<CompositeIdCollectionParent> findByTenantIdAndRefId(String tenantId, String refId);

    /**
     * Loads a parent by a non-identity property, so a parent whose composite identity is only
     * half populated can still be fetched with its join.
     *
     * @param name the parent name
     * @return the parent, if any
     */
    @Join("children")
    Optional<CompositeIdCollectionParent> findByName(String name);
}
