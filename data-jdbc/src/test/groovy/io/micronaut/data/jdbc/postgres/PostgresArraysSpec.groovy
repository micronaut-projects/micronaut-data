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
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.DataType
import io.micronaut.data.model.query.builder.sql.Dialect
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
