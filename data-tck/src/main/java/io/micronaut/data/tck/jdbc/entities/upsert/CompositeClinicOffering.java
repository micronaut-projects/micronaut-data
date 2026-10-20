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
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import org.jspecify.annotations.Nullable;

import static io.micronaut.data.annotation.Relation.Kind.MANY_TO_ONE;

@MappedEntity
@Index(name = "uk_comp_clinic_offering", columns = {"clinic_region", "clinic_code", "service_code"}, unique = true)
public class CompositeClinicOffering {

    @Id
    @GeneratedValue
    @Nullable
    private Integer id;

    @Relation(MANY_TO_ONE)
    private CompositeClinic clinic;

    private String serviceCode;

    private String name;

    public CompositeClinicOffering() {
    }

    public CompositeClinicOffering(CompositeClinic clinic, String serviceCode, String name) {
        this.clinic = clinic;
        this.serviceCode = serviceCode;
        this.name = name;
    }

    @Nullable
    public Integer getId() {
        return id;
    }

    public void setId(@Nullable Integer id) {
        this.id = id;
    }

    public CompositeClinic getClinic() {
        return clinic;
    }

    public void setClinic(CompositeClinic clinic) {
        this.clinic = clinic;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public void setServiceCode(String serviceCode) {
        this.serviceCode = serviceCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
