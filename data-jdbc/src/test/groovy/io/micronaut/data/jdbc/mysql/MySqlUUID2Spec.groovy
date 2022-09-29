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

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MySqlUUID2Spec extends Specification implements MySQLTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(properties)

    @Shared
    MySqlUuidEntity2Repository repository = applicationContext.getBean(MySqlUuidEntity2Repository)

    void 'test insert with UUID'() {
        given:
            def entity = new MySqlUuidEntity2()
            entity.name = "SPECIAL"
        when:
            MySqlUuidEntity2 test = repository.save(entity)
        then:
            test.id
        when:
            def test2 = repository.findById(test.id).get()
        then:
            test2.id == test.id
            test2.name == 'SPECIAL'
        when: "update with auto populated id param"
            test2.name = "UPDATED"
            repository.update(test2)
            test2 = repository.findById(test.id).get()
        then:
            test2.name == "UPDATED"
            test2.id == test.id
        when: "update query with transform is used"
            repository.update(test.id, "xyz")
            test2 = repository.findById(test.id).get()
        then:
            test2.id == test.id
            test2.name == 'xyz'
        when:
            def result = repository.getByIdInList([test2.id])
        then:
            result.id == test2.id
            result.name == 'xyz'
        when:
            repository.delete(result)
        then:
            !repository.findById(result.id).isPresent()
        cleanup:
            repository.deleteAll()
    }

}
