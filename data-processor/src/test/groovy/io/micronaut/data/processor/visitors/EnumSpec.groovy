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
package io.micronaut.data.processor.visitors

class EnumSpec extends AbstractDataSpec {

    void "test handle enum type match"() {
        when:
        def repo = buildRepository('test.MyInterface', """

import io.micronaut.data.tck.entities.Pet;
import io.micronaut.data.tck.entities.Pet.PetType;

@Repository
interface MyInterface extends GenericRepository<Pet, Long> {

    Pet findByType(PetType type);
}
""")

        then:
        repo != null
    }
}
