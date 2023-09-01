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
package io.micronaut.data.hibernate

import io.micronaut.data.hibernate.entities.RelPerson
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared

@MicronautTest(packages = "io.micronaut.data.tck.entities", rollback = false, transactional = false)
@H2DBProperties
class HibernateQuerySpec extends AbstractHibernateQuerySpec {

    @Shared
    @Inject
    RelPersonRepository relPersonRepo

    void "test relations person repository and joins"() {
        given:
        def parent = new RelPerson()
        parent.name = 'RelParent'
        relPersonRepo.save(parent)
        def child1Friend1 = new RelPerson()
        child1Friend1.name = 'Child1Friend1'
        relPersonRepo.save(child1Friend1)
        def child1Friend2 = new RelPerson()
        child1Friend2.name = 'Child1Friend2'
        relPersonRepo.save(child1Friend2)
        def child2Friend1 = new RelPerson()
        child2Friend1.name = 'Child2Friend1'
        relPersonRepo.save(child2Friend1)
        def child1 = new RelPerson()
        child1.name = 'Child1'
        child1.parent = parent
        child1.friends = [child1Friend1, child1Friend2]
        relPersonRepo.save(child1)
        def child2 = new RelPerson()
        child2.name = 'Child2'
        child2.parent = parent
        child2.friends = [child2Friend1]
        relPersonRepo.save(child2)
        when:
        def result = (List<RelPerson>) relPersonRepo.findAll(RelPersonRepository.Specifications.findRelPersonByParentAndFriends(parent.id, List.of(child1Friend1.id, child1Friend2.id)))
        then:
        result.size() == 1
        result[0].id == child1.id
        when:
        result = (List<RelPerson>) relPersonRepo.findAll(RelPersonRepository.Specifications.findRelPersonByParentAndFriends(parent.id, List.of(child1Friend1.id, child1Friend2.id, child2Friend1.id)))
        then:
        result.size() == 2
        when:
        result = (List<RelPerson>) relPersonRepo.findAll(RelPersonRepository.Specifications.findRelPersonByChildren(List.of(child1.id, child2.id)))
        then:
        result.size() == 1
        result[0].id == parent.id
        when:
        result = (List<RelPerson>) relPersonRepo.findAll(RelPersonRepository.Specifications.findRelPersonByChildren(List.of(child1Friend1.id, child1Friend2.id, child2Friend1.id)))
        then:
        result.size() == 0
    }
}
