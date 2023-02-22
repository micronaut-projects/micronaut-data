/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.model.annotation

import io.micronaut.data.annotation.Join
import spock.lang.Specification

class JoinTypeSpec extends Specification {

    def "test Join.Type#isFetch"() {
        expect:
        Join.Type.FETCH.isFetch()
        Join.Type.LEFT_FETCH.isFetch()
        Join.Type.RIGHT_FETCH.isFetch()
        Join.Type.OUTER_FETCH.isFetch()
        !Join.Type.LEFT.isFetch()
        !Join.Type.RIGHT.isFetch()
        !Join.Type.DEFAULT.isFetch()
        !Join.Type.INNER.isFetch()
        !Join.Type.OUTER.isFetch()
    }
}
