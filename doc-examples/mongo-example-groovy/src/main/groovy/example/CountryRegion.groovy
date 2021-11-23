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
package example

import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.model.naming.NamingStrategies

// tag::namingStrategy[]
@MappedEntity(namingStrategy = NamingStrategies.Raw.class)
class CountryRegion {
// end::namingStrategy[]

    @GeneratedValue
    @Id
    private Long id
    private String name

    @Relation(Relation.Kind.MANY_TO_ONE)
    private Country country

    CountryRegion(String name, @Nullable Country country) {
        this.name = name
        this.country = country
    }

    Long getId() {
        return id
    }

    void setId(Long id) {
        this.id = id
    }

    String getName() {
        return name
    }

    Country getCountry() {
        return country
    }

}
