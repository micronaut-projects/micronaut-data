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
