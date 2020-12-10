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
//        when:
//            multiArrayEntityRepository.update(entityStored.id,
//                    [["OOO", "ZZZ"], ["CCC", "DDD"], ["123", "456"]] as String[][]
//            )
//            entityStored = multiArrayEntityRepository.findById(entity.id).get()
//        then:
//            entityStored.stringMultiArray == [["OOO", "ZZZ"], ["CCC", "DDD"], ["123", "456"]] as String[][]
    }

}
