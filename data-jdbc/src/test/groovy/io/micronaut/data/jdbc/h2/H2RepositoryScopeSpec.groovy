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

import io.micronaut.context.BeanContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
@H2DBProperties
class H2RepositoryScopeSpec extends Specification {

    @Inject
    @Shared BeanContext beanContext

    void "test default repository scope is prototype"() {
        when:
            def instance1 = beanContext.getBean(H2BookRepository)
            def instance2 = beanContext.getBean(H2BookRepository)
        then:
            !instance1.is(instance2)
    }

    void "test explicit singleton repository scope is honored"() {
        when:
            def instance1 = beanContext.getBean(H2BookDtoRepository)
            def instance2 = beanContext.getBean(H2BookDtoRepository)
        then:
            instance1.is(instance2)
    }
}
