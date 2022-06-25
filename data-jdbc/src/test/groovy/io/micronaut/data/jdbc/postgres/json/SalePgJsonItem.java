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
package io.micronaut.data.jdbc.postgres.json;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;

import java.util.Map;
import java.util.Objects;

@MappedEntity
public class SalePgJsonItem {
    @GeneratedValue
    @Id
    private Long id;

    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
    @MappedProperty("fk_sale_id")
    private SalePgJson sale;

    private String name;

    @MappedProperty(definition = "JSON")
    @TypeDef(type = DataType.JSON)
    @Nullable
    private Map<String, String> data;


  public SalePgJsonItem(Long id, SalePgJson sale, String name, @Nullable Map<String, String> data) {
      this.id = id;
      this.sale = sale;
      this.name = name;
      this.data = data;
  }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SalePgJson getSale() {
        return sale;
    }

    public void setSale(SalePgJson sale) {
        this.sale = sale;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SalePgJsonItem)) {
            return false;
        }

        SalePgJsonItem saleItem = (SalePgJsonItem) o;

        return Objects.equals(id, saleItem.id) &&
               Objects.equals(name, saleItem.name) &&
               Objects.equals(data, saleItem.data);
    }

    @Override
    public int hashCode() {
       return Objects.hash(id, sale, name, data);
    }

    @Override
    public String toString() {
        return "Sale2Item{" + "id=" + id + ", name='" + name + "', data=" + data + '}';
    }
}
