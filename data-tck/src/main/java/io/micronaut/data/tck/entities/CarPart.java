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

import jakarta.persistence.*;

@Entity
@Table(name = "car_parts", schema = "ford", catalog = "ford_cat")
@javax.persistence.Entity
@javax.persistence.Table(name = "car_parts", schema = "ford", catalog = "ford_cat")
public class CarPart {

    @GeneratedValue
    @Id
    @javax.persistence.GeneratedValue
    @javax.persistence.Id
    private Long partId;

    private String name;
    @ManyToOne
    @javax.persistence.ManyToOne
    private Car car;

    public Car getCar() {
        return car;
    }

    public void setCar(Car car) {
        this.car = car;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getPartId() {
        return partId;
    }

    public void setPartId(Long partId) {
        this.partId = partId;
    }
}
