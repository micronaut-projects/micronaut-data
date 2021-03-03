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
package io.micronaut.data.jdbc.h2


import io.micronaut.data.tck.entities.Task
import io.micronaut.data.tck.entities.TaskGenericEntity
import io.micronaut.data.tck.entities.TaskGenericEntity2
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@H2DBProperties
class H2CustomIdSpec extends Specification {

    @Inject
    @Shared
    H2TaskRepository taskRepository

    @Inject
    @Shared
    H2TaskGenericEntityRepository taskGenericEntityRepository
    @Inject
    @Shared
    H2TaskGenericEntity2Repository taskGenericEntity2Repository

    void "test save and read entity"() {
        when:"an entity is saved"
        def task = taskRepository.save(new Task("Task 1"))

        then:
        task.taskId

        when:"An entity is retrieved by ID"
        task = taskRepository.findById(task.taskId).orElse(null)

        then:"The entity is correct"
        task.taskId
        task.name == "Task 1"
    }

    void "test save and read generic entity"() {
        when:
        def task = taskGenericEntityRepository.save(new TaskGenericEntity("Task 1"))

        then:
        task.id

        when:
        task = taskGenericEntityRepository.findById(task.id).orElse(null)

        then:
        task.id
        task.name == "Task 1"
    }

    void "test save and read generic entity2 "() {
        when:
        def task = taskGenericEntity2Repository.save(new TaskGenericEntity2("Task 1"))

        then:
        task.id

        when:
        task = taskGenericEntity2Repository.findById(task.id).orElse(null)

        then:
        task.id
        task.name == "Task 1"
    }

}
