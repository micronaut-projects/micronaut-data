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
package io.micronaut.data.jdbc.mysql

import io.micronaut.data.tck.tests.AbstractUUIDSpec
import spock.lang.Shared

class MySqlUUIDSpec extends AbstractUUIDSpec implements MySQLTestPropertyProvider {

    @Shared
    MySqlUuidEntityRepository repository = applicationContext.getBean(MySqlUuidEntityRepository)

    MySqlUuidRepository uuidRepository = applicationContext.getBean(MySqlUuidRepository)

    void 'test insert with UUID'() {
        given:
            def entity = new MySqlUuidEntity()
            entity.id = UUID.randomUUID()
            entity.id2 = UUID.randomUUID()
            entity.id3 = UUID.randomUUID()
            entity.id4 = UUID.randomUUID()
            entity.id5 = UUID.randomUUID()
            entity.id6 = UUID.randomUUID()
            entity.name = "SPECIAL"
        when:
            MySqlUuidEntity test = repository.save(entity)
        then:
            test.id
        when:
            def test2 = repository.findById(test.id).get()
        then:
            test2.id == test.id
            test2.id2 == test.id2
            test2.id3 == test.id3
            test2.id4 == test.id4
            test2.id5 == test.id5
            test2.id6 == test.id6
            test2.name == 'SPECIAL'
        when: "update query with transform is used"
            def newUUID = UUID.randomUUID()
            repository.update(test.id, newUUID)
            test2 = repository.findById(test.id).get()
        then:
            test2.id == test.id
            test2.id2 == newUUID
            test2.id3 == test.id3
            test2.id4 == test.id4
            test2.id5 == test.id5
            test2.id6 == test.id6
            test2.name == 'SPECIAL'
        when:
            def result = repository.getById3InList([test2.id3])
        then:
            result.id == test2.id
            result.id2 == test2.id2
            result.id3 == test2.id3
            result.id4 == test2.id4
            result.id5 == test2.id5
            result.id6 == test2.id6
            result.name == 'SPECIAL'
        when:
            repository.delete(result)
        then:
            !repository.findById(result.id).isPresent()
        cleanup:
            repository.deleteAll()
    }

}
