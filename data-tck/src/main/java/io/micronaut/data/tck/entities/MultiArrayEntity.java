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

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;

import java.util.Arrays;
import java.util.Objects;

@MappedEntity
public class MultiArrayEntity {
    @GeneratedValue
    @Id
    private Long id;
    @MappedProperty(definition = "text[][]")
    private String[][] stringMultiArray;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String[][] getStringMultiArray() {
        return stringMultiArray;
    }

    public void setStringMultiArray(String[][] stringMultiArray) {
        this.stringMultiArray = stringMultiArray;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MultiArrayEntity)) return false;
        MultiArrayEntity that = (MultiArrayEntity) o;
        return Objects.equals(id, that.id) &&
                Arrays.deepEquals(stringMultiArray, that.stringMultiArray);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id);
        result = 31 * result + Arrays.hashCode(stringMultiArray);
        return result;
    }
}
