package io.micronaut.data.model.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

@MappedEntity(value = "car_parts", schema = "ford")
public class MappedEntityCarPart {

    @GeneratedValue
    @Id
    private Long partId;

    private String name;
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private MappedEntityCar car;

    public MappedEntityCar getCar() {
        return car;
    }

    public void setCar(MappedEntityCar car) {
        this.car = car;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getPartId() {
        return partId;
    }

    public void setPartId(Long partId) {
        this.partId = partId;
    }
}
