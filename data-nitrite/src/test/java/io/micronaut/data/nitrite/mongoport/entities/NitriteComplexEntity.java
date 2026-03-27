package io.micronaut.data.nitrite.mongoport.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.ArrayList;
import java.util.List;

@MappedEntity("nitrite_complex_entity")
public class NitriteComplexEntity {
    @Id
    @GeneratedValue
    private String id;
    private String name;

    @Relation(value = Relation.Kind.ONE_TO_ONE)
    private NitriteComplexValue value;

    @Relation(value = Relation.Kind.ONE_TO_MANY, cascade = Relation.Cascade.ALL)
    private List<NitriteComplexValue> values = new ArrayList<>();

    public NitriteComplexEntity() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public NitriteComplexValue getValue() {
        return value;
    }

    public void setValue(NitriteComplexValue value) {
        this.value = value;
    }

    public List<NitriteComplexValue> getValues() {
        return values;
    }

    public void setValues(List<NitriteComplexValue> values) {
        this.values = values;
    }
}
