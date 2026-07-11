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

import io.micronaut.data.annotation.Query;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.UuidTestEntity;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NitriteRepository
public interface UuidTestRepository extends CrudRepository<UuidTestEntity, UUID> {

    @Query("{}")
    List<UuidTestEntity> findAll();

    /**
     * KNOWN BUG: @Query with field filter for UUID strings returns empty.
     * Field name in JSON (canonicalName) doesn't match stored field (canonical_name).
     */
    @Query("{\"canonicalName\": {\"$eq\": :canonicalName}}")
    Optional<UuidTestEntity> findByCanonicalName(String canonicalName);
}
