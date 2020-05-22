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

import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.repositories.PersonReactiveRepository
import io.micronaut.data.tck.tests.AbstractReactiveRepositorySpec
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared

import javax.inject.Inject

@MicronautTest(rollback = false)
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
class H2ReactiveRepositorySpec extends AbstractReactiveRepositorySpec {

    @Inject
    @Shared
    PersonReactiveRepository personReactiveRepository

    @Override
    PersonReactiveRepository getPersonRepository() {
        return personReactiveRepository
    }

    @Override
    void init() {
    }
}
