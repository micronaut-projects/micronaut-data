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
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

@MappedEntity
@Index(columns = {"sku", "warehouse"}, unique = true)
public class WarehouseInventory {

    @Id
    @GeneratedValue
    @Nullable
    private Long id;

    @NotBlank
    private String sku;

    @NotBlank
    private String warehouse;

    @NotNull
    private Integer quantity;

    public WarehouseInventory() {
    }

    public WarehouseInventory(String sku, String warehouse, Integer quantity) {
        this(null, sku, warehouse, quantity);
    }

    public WarehouseInventory(@Nullable Long id, String sku, String warehouse, Integer quantity) {
        this.id = id;
        this.sku = sku;
        this.warehouse = warehouse;
        this.quantity = quantity;
    }

    @Nullable
    public Long getId() {
        return id;
    }

    public void setId(@Nullable Long id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(String warehouse) {
        this.warehouse = warehouse;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
