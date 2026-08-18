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

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.NitriteComplexEntity;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

import java.util.List;

@NitriteRepository
public interface NitriteComplexEntityRepository extends CrudRepository<NitriteComplexEntity, String>, PageableRepository<NitriteComplexEntity, String>, JpaSpecificationExecutor<NitriteComplexEntity> {
    /**
     * {@code values} is an association whose target has no identity, so a reverse lookup for it
     * cannot be resolved through a sub-query.
     *
     * @param key the key to match
     * @return the matching entities
     */
    List<NitriteComplexEntity> findByValuesKey(String key);
}
