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

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.model.DataType;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class EmbeddedIdExampleId implements Serializable {

    @MappedProperty(value = "p", type = DataType.STRING)
    private final String p;

    @MappedProperty(value = "t", type = DataType.STRING)
    private final String t;

    public EmbeddedIdExampleId(String p, String t) {
        this.p = p;
        this.t = t;
    }

    public String getP() {
        return p;
    }

    public String getT() {
        return t;
    }

    @Override
    public String toString() {
        return "TableId{" +
                "p='" + p + '\'' +
                ", t='" + t + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmbeddedIdExampleId tableId = (EmbeddedIdExampleId) o;
        return Objects.equals(p, tableId.p) &&
                Objects.equals(t, tableId.t);
    }

    @Override
    public int hashCode() {
        return Objects.hash(p, t);
    }
}
