/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.tck.jdbc.entities.upsert;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static io.micronaut.data.annotation.Relation.Kind.ONE_TO_MANY;

@MappedEntity
public class Clinic {

    @Id
    @GeneratedValue
    @Nullable
    private Integer id;

    @NotBlank
    private String name;

    @Relation(value = ONE_TO_MANY, mappedBy = "clinic")
    private List<ClinicServiceOffering> serviceOfferings = new ArrayList<>();

    public Clinic() {
    }

    public Clinic(String name) {
        this.name = name;
    }

    @Nullable
    public Integer getId() {
        return id;
    }

    public void setId(@Nullable Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ClinicServiceOffering> getServiceOfferings() {
        return serviceOfferings;
    }

    public void setServiceOfferings(List<ClinicServiceOffering> serviceOfferings) {
        this.serviceOfferings = serviceOfferings;
    }
}
