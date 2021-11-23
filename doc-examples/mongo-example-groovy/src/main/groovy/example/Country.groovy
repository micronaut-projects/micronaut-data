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


import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import org.bson.types.ObjectId

// tag::country[]
@MappedEntity // <1>
class Country {

    @Id
    private ObjectId id // <2>

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "country")
    private Set<CountryRegion> regions // <3>

    private String name // <4>

    // end::country[]
    Country(String name) {
        this.name = name
    }

    String getName() {
        return name
    }

    ObjectId getId() {
        return id
    }

    void setId(ObjectId id) {
        this.id = id
    }

    void setName(String name) {
        this.name = name
    }

    Set<CountryRegion> getRegions() {
        return regions
    }

    void setRegions(Set<CountryRegion> regions) {
        this.regions = regions
    }
    // tag::country[]
    // ...
}
// end::country[]
