package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;

import java.util.Map;

@Introspected
public class SaleDTO {
    private final String name;
    private final Map<String, String> data;

    public SaleDTO(String name, Map<String, String> data) {
        this.name = name;
        this.data = data;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getData() {
        return data;
    }
}
