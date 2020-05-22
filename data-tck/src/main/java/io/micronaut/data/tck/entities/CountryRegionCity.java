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
package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.naming.NamingStrategies;

@MappedEntity(namingStrategy = NamingStrategies.Raw.class)
public class CountryRegionCity {
    private final CountryRegion countryRegion;
    private final City city;

    public CountryRegionCity(CountryRegion countryRegion, City city) {
        this.countryRegion = countryRegion;
        this.city = city;
    }

    @Relation(Relation.Kind.MANY_TO_ONE)
    public CountryRegion getCountryRegion() {
        return countryRegion;
    }

    @Relation(Relation.Kind.MANY_TO_ONE)
    public City getCity() {
        return city;
    }
}
