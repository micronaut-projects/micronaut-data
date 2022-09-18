/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.tck.repositories;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.UuidNullEntity;

import java.util.Collection;
import java.util.UUID;

public interface UuidNullRepository extends CrudRepository<UuidNullEntity, UUID> {

    @Query(
        value = "select * from uuid_null " +
            "where :value is null or :value is not null and :value = nullable_uuid",
        nativeQuery = true
    )
    Collection<UuidNullEntity> findByUuid(UUID value);

}
