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
package io.micronaut.data.tck.tests

import edu.umd.cs.findbugs.annotations.NonNull
import io.micronaut.data.annotation.Join
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.Category
import io.micronaut.data.tck.entities.Product
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.repositories.CategoryRepository
import io.micronaut.data.tck.repositories.PersonRepository
import io.micronaut.data.tck.repositories.ProductRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

import static java.util.stream.Collectors.toMap

@MicronautTest
abstract class AbstractJoinSpec extends Specification {

    abstract CategoryRepository getCategoryRepository()

    abstract ProductRepository getProductRepository()

    abstract void init()

    def setupSpec() {
        init()
    }

    def cleanup() {
        productRepository?.deleteAll()
        categoryRepository?.deleteAll()
    }

    void "forcibly add order to list method annotated with join"() {
        Map<Long, Category> categoriesInserted = new HashMap<>()

        when: "Inserting categories and products"
        Category category = new Category()
        category.setName("Category#1")
        category.setPosition(1)

        Category category2 = new Category()
        category2.setName("Category#2")
        category.setPosition(2)

        Category category3 = new Category()
        category3.setName("Category#1")
        category.setPosition(3)

        category = categoryRepository.save(category)
        category3 = categoryRepository.save(category3)
        category2 = categoryRepository.save(category2)

        for (j in 1..6) {
            Product product = new Product()
            product.setName("Product#"+j)
            product.changePrice(j)

            if (j%2==0) {
                //product.setCategoryId(category2.getId())
                product.setCategory(category2)
            }
            else {
                //product.setCategoryId(category.getId())
                product.setCategory(category)
            }

            product = productRepository.save(product)

            if (j%2==0) {
                category2.getProductList().add(product)
            }
            else {
                category.getProductList().add(product)
            }
        }

        categoriesInserted.put(category.getId(), category)
        categoriesInserted.put(category2.getId(), category2)
        categoriesInserted.put(category3.getId(), category3)

        then:
        categoryRepository.count() == categoriesInserted.size()

        when: "Join and no order"
        Collection<Category> categories = categoryRepository.findAll()
        Map<Long, Category> categoriesMap = categories.stream().collect(toMap(a -> a.getId() , a -> a));

        then:
        categories.size() == categoriesInserted.size()
        categoriesMap == categoriesInserted

        when: "Join and order by id"
        categories = categoryRepository.findAllOrderById()
        categoriesMap = categories.stream().collect(toMap(a -> a.getId() , a -> a));

        then:
        categories.size() == categoriesInserted.size()
        categoriesMap == categoriesInserted

        when: "Join and order by name"
        categories = categoryRepository.findAllOrderByName()
        categoriesMap = categories.stream().collect(toMap(a -> a.getId() , a -> a));

        then:
        categories.size() == categoriesInserted.size()
        categoriesMap == categoriesInserted

        when: "Join and order by position and name"
        categories = categoryRepository.findAllOrderByPositionAndName()
        categoriesMap = categories.stream().collect(toMap(a -> a.getId() , a -> a));

        then:
        categories.size() == categoriesInserted.size()
        categoriesMap == categoriesInserted

        when: "Join and Pageable sorting by association"
        categories = categoryRepository.findAll(Pageable.from(
                                                Sort.of(Sort.Order.desc("name")
                                                ))).findAll()
        categoriesMap = categories.stream().collect(toMap(a -> a.getId() , a -> a));

        then:
        categories.size() == categoriesInserted.size()
        categoriesMap == categoriesInserted
    }
}