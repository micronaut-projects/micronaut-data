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
package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;

/**
 * Entity with two indexed fields of different types (String and Long), used to regression-test
 * AND-combined derived queries that mix an equality filter on one indexed field with a range
 * filter on a differently-typed indexed field (nitrite 4.4.2 fixed a planner
 * ClassCastException for this exact shape).
 */
@MappedEntity
public class IndexedOrder {
    @Id
    @GeneratedValue
    private Long id;

    @Index(columns = "status")
    private String status;

    @Index(columns = "amount")
    private Long amount;

    // Unindexed, nullable: used to regression-test null-key sort comparator behavior
    // alongside the indexed `status` field, which is also nullable.
    private String label;

    public IndexedOrder() {
    }

    public IndexedOrder(String status, Long amount) {
        this.status = status;
        this.amount = amount;
    }

    public IndexedOrder(String status, Long amount, String label) {
        this.status = status;
        this.amount = amount;
        this.label = label;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
