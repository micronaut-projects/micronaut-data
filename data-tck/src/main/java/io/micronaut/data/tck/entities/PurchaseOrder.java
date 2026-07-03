/*
 * Copyright 2017-2026 original authors
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.micronaut.data.tck.entities;

import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;

@SuppressWarnings("checkstyle:DesignForExtension")
@Entity
public class PurchaseOrder {

    @EmbeddedId
    private OrderPk id;

    private String description;

    @Embedded
    private EmbeddableClass details;

    public OrderPk getId() {
        return id;
    }

    public void setId(OrderPk id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public EmbeddableClass getDetails() {
        return details;
    }

    public void setDetails(EmbeddableClass details) {
        this.details = details;
    }
}

