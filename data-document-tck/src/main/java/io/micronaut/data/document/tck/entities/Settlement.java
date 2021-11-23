/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.document.tck.entities;

import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;

@MappedEntity("comp_settlement")
public class Settlement {

    @EmbeddedId
    private SettlementPk id;
    @MappedProperty
    private String description;
    @Relation(Relation.Kind.MANY_TO_ONE)
    private SettlementType settlementType;
    @Relation(Relation.Kind.MANY_TO_ONE)
    private Zone zone;
    @MappedProperty("is_enabled")
    private Boolean enabled;

    public SettlementPk getId() {
        return id;
    }

    public void setId(SettlementPk id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SettlementType getSettlementType() {
        return settlementType;
    }

    public void setSettlementType(SettlementType settlementType) {
        this.settlementType = settlementType;
    }

    public Zone getZone() {
        return zone;
    }

    public void setZone(Zone zone) {
        this.zone = zone;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
