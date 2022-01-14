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
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;

import java.util.List;
import java.util.Map;
import java.util.Set;

@MappedEntity
public class Sale {
    @GeneratedValue
    @Id
    private Long id;

    private String name;

    @TypeDef(type = DataType.JSON)
    @Nullable
    private Map<String, String> data;

    @TypeDef(type = DataType.JSON)
    @Nullable
    private String extraData;

    @TypeDef(type = DataType.JSON)
    @Nullable
    private Map<String, Integer> quantities;

    @TypeDef(type = DataType.JSON)
    @Nullable
    private List<String> dataList;

    @Relation(
        value = Relation.Kind.ONE_TO_MANY,
        mappedBy = "sale")
    private Set<SaleItem> items;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getData() {
        return data;
    }

    public void setData(Map<String, String> data) {
        this.data = data;
    }

    public Map<String, Integer> getQuantities() {
        return quantities;
    }

    public void setQuantities(Map<String, Integer> quantities) {
        this.quantities = quantities;
    }

    public String getExtraData() {
        return extraData;
    }

    public void setExtraData(String extraData) {
        this.extraData = extraData;
    }

    public Set<SaleItem> getItems() {
        return items;
    }

    public void setItems(Set<SaleItem> items) {
        this.items = items;
    }

    @Nullable
    public List<String> getDataList() {
        return dataList;
    }

    public void setDataList(@Nullable List<String> dataList) {
        this.dataList = dataList;
    }
}
