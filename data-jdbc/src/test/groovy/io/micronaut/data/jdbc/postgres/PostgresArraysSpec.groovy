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
package io.micronaut.data.jdbc.postgres

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.DataType
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.PageableRepository
import io.micronaut.data.tck.entities.MultiArrayEntity
import io.micronaut.data.tck.repositories.ArraysEntityRepository
import io.micronaut.data.tck.repositories.MultiArrayEntityRepository
import io.micronaut.data.tck.tests.AbstractArraysSpec

class PostgresArraysSpec extends AbstractArraysSpec implements PostgresTestPropertyProvider {

    @Override
    ArraysEntityRepository getArraysEntityRepository() {
        return context.getBean(PostgresArraysEntityRepository)
    }

    MultiArrayEntityRepository getMultiArrayEntityRepository() {
        return context.getBean(PostgresMultiArrayEntityRepository)
    }

    def "should insert and update an entity with multi array"() {
        given:
            MultiArrayEntity entity = new MultiArrayEntity()
            entity.stringMultiArray = [["AAA", "BBB"], ["CCC", "DDD"], ["EEE", "FFF"]] as String[][]
        when:
            multiArrayEntityRepository.save(entity)
            MultiArrayEntity entityStored = multiArrayEntityRepository.findById(entity.id).get()
        then:
            entityStored == entity
        when:
            entity.stringMultiArray = [["XXX", "ZZZ"], ["CCC", "DDD"], ["EEE", "FFF"]] as String[][]
            multiArrayEntityRepository.update(entity)
            entityStored = multiArrayEntityRepository.findById(entity.id).get()
        then:
            entityStored == entity
        when:
            multiArrayEntityRepository.update(entityStored.id,
                    [["OOO", "ZZZ"], ["CCC", "DDD"], ["123", "456"]] as String[][]
            )
            entityStored = multiArrayEntityRepository.findById(entity.id).get()
        then:
            entityStored.stringMultiArray == [["OOO", "ZZZ"], ["CCC", "DDD"], ["123", "456"]] as String[][]
    }

    def "empty array"() {
        given:
            def repo = context.getBean(Repo)
        when:
            def e = repo.save(new Ent(null, new String[0]))
        then:
            e.strings.length == 0
        when:
            e = repo.findById(1L).get()
        then:
            e.strings.length == 0
    }

    def "custom query binds UUID array as postgres array parameter"() {
        given:
            def repo = context.getBean(UuidArrayItemRepository)
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
            def repo = context.getBean(UuidArrayItemRepository)
            List<UUID> ids = [UUID.randomUUID(), UUID.randomUUID()]

        when:
            repo.batchInsertByIds(ids)

        then:
            ids.every { repo.findById(it).present }

        cleanup:
            repo.deleteAll()
    }

}

@MappedEntity("pg_arrayz")
class Ent {
    @Id
    @GeneratedValue
    Long id
    @MappedProperty(definition = "VARCHAR(255) []", type = DataType.STRING_ARRAY)
    String[] strings

    Ent(Long id, String[] strings) {
        this.id = id
        this.strings = strings
    }
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface Repo extends PageableRepository<Ent, Long> {
}

@MappedEntity("pg_uuid_array_item")
class UuidArrayItem {
    @Id
    UUID id
    String name
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface UuidArrayItemRepository extends CrudRepository<UuidArrayItem, UUID> {

    @Query("""
        INSERT INTO pg_uuid_array_item (id, name)
        SELECT ids.id, 'batch' FROM unnest(cast(:ids AS uuid[])) AS ids(id)
        ON CONFLICT (id) DO NOTHING
    """)
    void batchInsertByIds(UUID[] ids)

    @Query("""
        INSERT INTO pg_uuid_array_item (id, name)
        SELECT ids.id, 'batch' FROM unnest(cast(:ids AS uuid[])) AS ids(id)
        ON CONFLICT (id) DO NOTHING
    """)
    void batchInsertByIds(@TypeDef(type = DataType.UUID_ARRAY) List<UUID> ids)
}
