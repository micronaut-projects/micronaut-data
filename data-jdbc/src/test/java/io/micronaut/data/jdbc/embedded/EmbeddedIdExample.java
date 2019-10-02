/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.jdbc.embedded;

import io.micronaut.core.annotation.Creator;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.model.DataType;

import java.util.Objects;

@MappedEntity(value = "Table1")
public class EmbeddedIdExample {

    @Creator
    public EmbeddedIdExample(EmbeddedIdExampleId tableId, String field) {
        this.tableId = tableId;
        this.field = field;
    }

    @EmbeddedId
    private EmbeddedIdExampleId tableId;

    @MappedProperty(value = "field", type = DataType.STRING)
    private String field;

    public EmbeddedIdExampleId getTableId() {
        return tableId;
    }

    public void setTableId(EmbeddedIdExampleId tableId) {
        this.tableId = tableId;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    @Override
    public String toString() {
        return "Table{" +
                "tableId=" + tableId +
                ", field='" + field + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmbeddedIdExample table = (EmbeddedIdExample) o;
        return Objects.equals(tableId, table.tableId) &&
                Objects.equals(field, table.field);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableId, field);
    }
}