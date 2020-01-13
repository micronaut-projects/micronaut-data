package io.micronaut.data.tck.entities;

import javax.persistence.*;

@Entity
@Table(name = "car_parts", schema = "ford", catalog = "ford_cat")
public class CarPart {

    @GeneratedValue
    @Id
    private Long partId;

    private String name;
    @ManyToOne
    private Car car;

    public Car getCar() {
        return car;
    }

    public void setCar(Car car) {
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
