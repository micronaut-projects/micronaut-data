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
package io.micronaut.data.model.runtime

import io.micronaut.data.annotation.Relation
import io.micronaut.data.model.Association
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.tck.entities.Face
import io.micronaut.data.tck.entities.Nose
import spock.lang.Specification

class RuntimePersistentEntitySpec extends Specification {
    void 'test one-to-one'() {
        given:
        PersistentEntity face = PersistentEntity.of(Face)
        PersistentEntity nose = PersistentEntity.of(Nose)

        def noseAss = face.getPropertyByName("nose")
        expect:
        noseAss instanceof Association
        noseAss.kind == Relation.Kind.ONE_TO_ONE
        noseAss.isForeignKey()
    }

}
