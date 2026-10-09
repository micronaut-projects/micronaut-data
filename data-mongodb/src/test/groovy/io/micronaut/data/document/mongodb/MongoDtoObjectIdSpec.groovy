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
package io.micronaut.data.document.mongodb

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.mongodb.annotation.MongoAggregateQuery
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.types.ObjectId
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * DTO results whose id is stored as an ObjectId.
 */
class MongoDtoObjectIdSpec extends Specification implements MongoTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    LabelRepository labelRepository = applicationContext.getBean(LabelRepository)

    def cleanup() {
        labelRepository.deleteAll()
    }

    void "an aggregation DTO with the id and one other field is mapped"() {
        given:
            def label = labelRepository.save(new Label(name: "a"))
        when:
            def dtos = labelRepository.findIdAndName()
        then:
            dtos.size() == 1
            dtos[0] != null
            dtos[0].id == label.id
            dtos[0].name == "a"
    }

    void "an aggregation DTO with an ObjectId typed id is mapped"() {
        given:
            def label = labelRepository.save(new Label(name: "a"))
        when:
            def dtos = labelRepository.findObjectIdAndName()
        then:
            dtos.size() == 1
            dtos[0] != null
            dtos[0].id == new ObjectId(label.id)
            dtos[0].name == "a"
    }
}

@MongoRepository
interface LabelRepository extends CrudRepository<Label, String> {

    @MongoAggregateQuery('[{$project: {name: 1}}]')
    List<LabelDto> findIdAndName()

    @MongoAggregateQuery('[{$project: {name: 1}}]')
    List<LabelObjectIdDto> findObjectIdAndName()
}

@MappedEntity
class Label {
    @Id
    @GeneratedValue
    String id

    String name
}

@Introspected
class LabelDto {
    String id
    String name
}

@Introspected
class LabelObjectIdDto {
    ObjectId id
    String name
}
