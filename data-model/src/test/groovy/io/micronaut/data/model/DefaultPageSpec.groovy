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
package io.micronaut.data.model

import spock.lang.Specification

import static io.micronaut.data.model.DefaultPage.NO_TOTAL_SIZE
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Unroll

@MicronautTest
class DefaultPageSpec extends Specification {

    @Unroll
    void "test default page creation with content #content, pageable #pageable and total size #totalSize"() {
        given:
        def page = new DefaultPage<String>(content, pageable, totalSize)

        expect:
        page.content == content
        page.hasTotalSize() == (expectedTotalSize > NO_TOTAL_SIZE)
        page.size == pageable.size
        page.totalSize == expectedTotalSize

        where:
        content     | pageable              | totalSize     | expectedTotalSize
        ["test"]    | Pageable.from(1)      | 10            | 10
        ["test"]    | Pageable.from(1)      | null          | NO_TOTAL_SIZE
    }
}
