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

import io.micronaut.core.annotation.Creator;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "ORDERS")
@javax.persistence.Entity
@javax.persistence.Table(name = "ORDERS")
public class Order {
    @Id
    @GeneratedValue
    @javax.persistence.Id
    @javax.persistence.GeneratedValue
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
