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
package io.micronaut.data.jdbc.h2.groovy_static_repo


import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
@H2DBProperties
class H2GroovyStaticExtendedRepositorySpec extends Specification {

    @Inject
    TestEntityRepository entityRepository

    def "simple operation"() {
        def entity = new GTestEntity(name: "xxx")
        when:
            entityRepository.save(entity)
            entityRepository.update(entity.id, "zzz")
        then:
            noExceptionThrown()
    }

}
