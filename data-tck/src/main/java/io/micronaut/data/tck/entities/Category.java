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

import io.micronaut.data.annotation.DateCreated;

import io.micronaut.core.annotation.Nullable;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity(name = "jcat")
@javax.persistence.Entity(name = "jcat")
public class Category {
    @Id
    @GeneratedValue
    @javax.persistence.Id
    @javax.persistence.GeneratedValue
    private Long id;
    @NotBlank
    private String name;

    private int position;

    @OneToMany(mappedBy = "category")
    @javax.persistence.OneToMany(mappedBy = "category")
    @Nullable
    private Set<Product> productList = new HashSet<>();

    @DateCreated
    private LocalDateTime createDate;

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

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    @Nullable
    public Set<Product> getProductList() {
        return productList;
    }

    public void setProductList(@Nullable Set<Product> productList) {
        this.productList = productList;
    }

    public LocalDateTime getCreateDate() {
        return createDate;
    }

    public void setCreateDate(LocalDateTime createDate) {
        this.createDate = createDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Category category = (Category) o;
        return id.equals(category.id) &&
                position == category.position &&
                name.equals(category.name) &&
//                Objects.equals(createDate, category.createDate) &&
//                Failing on JDK 15 https://github.com/micronaut-projects/micronaut-data/pull/948
                productList.equals(category.productList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, position, productList);
//        return Objects.hash(id, name, position, productList, createDate);
    }

    @Override
    public String toString() {
        return "Category{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", position=" + position +
                ", productList=" + productList +
                ", createDate=" + createDate +
                '}';
    }
}
