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

import io.micronaut.data.intercept.annotation.DataMethod

class AbstractInheritanceSpec extends AbstractDataSpec {


    void "test compile repository that uses abstract inheritance"() {
        given:
        def repository = buildRepository('test.CustomerRepository', """

@javax.persistence.MappedSuperclass
abstract class BaseEntity {
    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.AUTO)
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

}

@javax.persistence.Entity
@javax.persistence.Table(name = "customer")
class Customer extends BaseEntity {

    @io.micronaut.data.annotation.Version
    private int version;

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}

@Repository
interface CustomerRepository extends io.micronaut.data.repository.CrudRepository<Customer, Long> {
}
""")
        expect:"The repository compiles"
        repository != null
    }
}
