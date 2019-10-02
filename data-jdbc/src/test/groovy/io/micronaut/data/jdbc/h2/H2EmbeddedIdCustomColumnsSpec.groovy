/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.context.annotation.Property
import io.micronaut.data.jdbc.embedded.EmbeddedIdExample
import io.micronaut.data.jdbc.embedded.EmbeddedIdExampleId
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
class H2EmbeddedIdCustomColumnsSpec extends Specification {

    @Inject
    H2EmbeddedIdExampleRepository repository

    void "test CRUD"() {
        when:
        EmbeddedIdExampleId id = new EmbeddedIdExampleId("a", "b")
        repository.save(new EmbeddedIdExample(id, "test"))

        def entity = repository.findById(id).orElse(null)

        then:
        entity != null
    }
}
