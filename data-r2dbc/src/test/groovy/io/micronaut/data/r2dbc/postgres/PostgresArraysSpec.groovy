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
package io.micronaut.data.r2dbc.postgres

import groovy.transform.Memoized
import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.model.DataType
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.tck.repositories.ArraysEntityRepository
import io.micronaut.data.tck.tests.AbstractArraysSpec

import java.util.UUID

class PostgresArraysSpec extends AbstractArraysSpec implements PostgresTestPropertyProvider {

    @Memoized
    @Override
    ArraysEntityRepository getArraysEntityRepository() {
        return context.getBean(PostgresArraysEntityRepository)
    }

    def "custom query binds UUID array as postgres array parameter"() {
        given:
            def repo = context.getBean(R2dbcUuidArrayItemRepository)
            UUID[] ids = [UUID.randomUUID(), UUID.randomUUID()] as UUID[]

        when:
            repo.batchInsertByIds(ids)

        then:
            ids.every { repo.findById(it).present }

        cleanup:
            repo.deleteAll()
    }

    def "custom query binds typed UUID list as postgres array parameter"() {
        given:
            def repo = context.getBean(R2dbcUuidArrayItemRepository)
            List<UUID> ids = [UUID.randomUUID(), UUID.randomUUID()]

        when:
            repo.batchInsertByIds(ids)

        then:
            ids.every { repo.findById(it).present }

        cleanup:
            repo.deleteAll()
    }

    def "custom query binds null UUID array as postgres array parameter"() {
        given:
            def repo = context.getBean(R2dbcUuidArrayItemRepository)

        expect:
            repo.countByIds(null as UUID[]) == 0
    }
}

@MappedEntity("r2dbc_uuid_array_item")
class R2dbcUuidArrayItem {
    @Id
    UUID id
    String name
}

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface R2dbcUuidArrayItemRepository extends CrudRepository<R2dbcUuidArrayItem, UUID> {

    @Query("""
        INSERT INTO r2dbc_uuid_array_item (id, name)
        SELECT ids.id, 'batch' FROM unnest(cast(:ids AS uuid[])) AS ids(id)
        ON CONFLICT (id) DO NOTHING
    """)
    void batchInsertByIds(UUID[] ids)

    @Query("""
        INSERT INTO r2dbc_uuid_array_item (id, name)
        SELECT ids.id, 'batch' FROM unnest(cast(:ids AS uuid[])) AS ids(id)
        ON CONFLICT (id) DO NOTHING
    """)
    void batchInsertByIds(@TypeDef(type = DataType.UUID_ARRAY) List<UUID> ids)

    @Query("""
        SELECT count(*) FROM r2dbc_uuid_array_item
        WHERE id = ANY(cast(:ids AS uuid[]))
    """)
    long countByIds(@Nullable @TypeDef(type = DataType.UUID_ARRAY) UUID[] ids)
}
