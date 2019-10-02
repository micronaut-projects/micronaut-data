/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.*;

@MappedEntity("T_CITY")
public class City {

    @Id
    @GeneratedValue
    private Long id;

    @MappedProperty("C_NAME")
    private String name;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private CountryRegion countryRegion;

    public City(String name, CountryRegion countryRegion) {
        this.name = name;
        this.countryRegion = countryRegion;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public CountryRegion getCountryRegion() {
        return countryRegion;
    }
}
