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
package io.micronaut.data.jdbc.postgres;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.UuidEntity;
import io.micronaut.data.tck.repositories.UuidRepository;

import java.util.Collection;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresUuidRepository extends UuidRepository {

    @Query(
        value = "select * from uuid_entity where " +
            ":param is null and nullable_value is null or " +
            ":param is not null and nullable_value = :param",
        nativeQuery = true
    )
    Collection<UuidEntity> findByNullableValueNative(@Nullable UUID param);
}
