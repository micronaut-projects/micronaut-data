package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;

import java.math.BigDecimal;

/**
 * Entity whose aggregated property is stored under a custom name that is not the snake-case form
 * of the Java property name.
 */
@MappedEntity
public class MappedAggregate {

    @Id
    @GeneratedValue
    private String id;

    private String name;

    @MappedProperty("grand_total")
    private BigDecimal totalValue;

    public MappedAggregate() {
    }

    public MappedAggregate(String name, BigDecimal totalValue) {
        this.name = name;
        this.totalValue = totalValue;
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

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }
}
