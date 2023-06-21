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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;


@MappedEntity
public class Restaurant {

    @GeneratedValue
    @Id
    private Long id;
    private final String name;

    @Relation(Relation.Kind.EMBEDDED)
    private final Address address;

    @Relation(Relation.Kind.EMBEDDED)
    @MappedProperty("hqaddress_")
    @Nullable
    private Address hqAddress;


    public Restaurant(String name, Address address) {
        this.name = name;
        this.address = address;
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

    public Address getAddress() {
        return address;
    }

    @Nullable
    public Address getHqAddress() {
        return hqAddress;
    }

    public void setHqAddress(@Nullable Address hqAddress) {
        this.hqAddress = hqAddress;
    }
}
