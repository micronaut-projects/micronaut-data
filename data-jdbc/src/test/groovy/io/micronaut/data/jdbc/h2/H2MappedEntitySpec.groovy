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


import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
@H2DBProperties
class H2MappedEntitySpec extends Specification {
    @Inject
    H2DoubleImplement1Repository di1
    @Inject
    H2DoubleImplement2Repository di2
    @Inject
    H2DoubleImplement3Repository di3

    void "test mapped entities with multiple interfaces"() {
        when: "First implementation"
        def results1 = di1.get()

        then: "The result is correct"
        results1 != null

        when: "Second implementation"
        def results2 = di2.get()

        then: "The result is correct"
        results2 != null

        when: "Second implementation"
        def results3 = di3.get()

        then: "The result is correct"
        results3 != null
    }
}
