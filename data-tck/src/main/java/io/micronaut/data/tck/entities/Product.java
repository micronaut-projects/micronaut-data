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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.MappedProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity(name = "jprod")
@javax.persistence.Entity(name = "jprod")
public class Product {
    @Id
    @GeneratedValue
    @javax.persistence.Id
    @javax.persistence.GeneratedValue
    private Long id;

    @ManyToOne
    @javax.persistence.ManyToOne
    private Category category;

    private String name;

    private BigDecimal price;

    @Nullable
    @MappedProperty(value = "loooooooooooooooooooooooooooooooooooooooooooooooooooooooong_name", alias = "long_name")
    private String longName;

    @DateCreated
    private LocalDateTime dateCreated;

    @DateUpdated
    private LocalDateTime lastUpdated;

    @Creator
    public Product(String name, BigDecimal price) {
        this.name = name;
        this.price = price;
    }

    // for hibernate
    protected Product(){}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getLongName() {
        return longName;
    }

    public void setLongName(String value) {
        this.longName = value;
    }

    public void setDateCreated(LocalDateTime dateCreated) {
        this.dateCreated = dateCreated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public LocalDateTime getDateCreated() {
        return dateCreated;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void changePrice(BigDecimal newPrice){
        price = newPrice;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return id.equals(product.id) &&
                name.equals(product.name) &&
                price.equals(product.price);
//                Failing on JDK15 https://github.com/micronaut-projects/micronaut-data/pull/948
//                Objects.equals(dateCreated, product.dateCreated) &&
//                Objects.equals(lastUpdated, product.lastUpdated);
    }

    @Override
    public int hashCode() {
//      Failing on JDK15 https://github.com/micronaut-projects/micronaut-data/pull/948
//      return Objects.hash(id, name, price, dateCreated, lastUpdated);
        return Objects.hash(id, name, price);
    }

    @Override
    public String toString() {
        return "Product{" +
                "id=" + id +
                ", name='" + name +
                ", price=" + price +
                ", dateCreated=" + dateCreated +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
