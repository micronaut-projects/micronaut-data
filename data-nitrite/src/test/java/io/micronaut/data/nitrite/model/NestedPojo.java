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

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Introspected
@Serdeable
public class NestedPojo {
    private String camelCaseField;
    private String snake_case_field;

    public NestedPojo() {}

    public NestedPojo(String camelCaseField, String snake_case_field) {
        this.camelCaseField = camelCaseField;
        this.snake_case_field = snake_case_field;
    }

    public String getCamelCaseField() {
        return camelCaseField;
    }

    public void setCamelCaseField(String camelCaseField) {
        this.camelCaseField = camelCaseField;
    }

    public String getSnake_case_field() {
        return snake_case_field;
    }

    public void setSnake_case_field(String snake_case_field) {
        this.snake_case_field = snake_case_field;
    }
}
