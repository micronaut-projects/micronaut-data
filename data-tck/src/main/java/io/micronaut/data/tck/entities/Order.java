package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Creator;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "ORDERS")
public class Order {
    @Id
    @GeneratedValue
    private Long id;

    private String customer;

    private BigDecimal totalAmount;

    private Double weight;

    private Integer units;

    private Long tax;

    @Creator
    public Order(
            String customer,
            BigDecimal totalAmount,
            Double weight,
            Integer units,
            Long tax) {
        this.customer = customer;
        this.totalAmount = totalAmount;
        this.weight = weight;
        this.units = units;
        this.tax = tax;
    }

    protected Order(){}

    public Long getId() {
        return id;
    }

    public String getCustomer() {
        return customer;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Double getWeight() {
        return weight;
    }

    public Integer getUnits() {
        return units;
    }

    public Long getTax() {
        return tax;
    }
}
