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

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.tck.entities.Category
import io.micronaut.data.tck.entities.Product
import io.micronaut.data.tck.repositories.CategoryRepository
import io.micronaut.data.tck.repositories.ProductDtoRepository
import io.micronaut.data.tck.repositories.ProductRepository
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import static java.util.stream.Collectors.toMap

abstract class AbstractJoinSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    abstract CategoryRepository getCategoryRepository()

    abstract ProductRepository getProductRepository()

    abstract ProductDtoRepository getProductDtoRepository()

    def cleanup() {
        productRepository?.deleteAll()
        categoryRepository?.deleteAll()
    }

    @Ignore
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
            product.setLongName("LongName#" + j);

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
        categories.forEach(c -> {
           c.getProductList().forEach(p -> {
              p.getLongName().startsWith("LongName#") == true
           });
        });
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

    void "test MappedProperty alias with DTO"() {
        given: "Inserting categories and products"
        Category category = new Category()
        category.setName("Category#1")
        category.setPosition(1)
        category = categoryRepository.save(category)

        Product product1 = new Product()
        product1.setName("Product#1")
        product1.changePrice(10)
        product1.setLongName("LongName1");
        product1.setCategory(category);
        product1 = productRepository.save(product1);

        Product product2 = new Product()
        product2.setName("Product#2")
        product2.changePrice(20)
        product2.setLongName("LongName2");
        product2.setCategory(category);
        product2 = productRepository.save(product2);

        when:
        def productOpt = productDtoRepository.findByNameWithQuery("Product#1")

        then:
        productOpt.isPresent()
        def product = productOpt.get()
        product.name == "Product#1"
        product.longName == "LongName1"
        product.price == 10

        when:
        def products = productDtoRepository.findByNameLikeOrderByName("Product%")

        then:
        products.size() == 2
        def productDto1 = products[0]
        productDto1.name == "Product#1"
        productDto1.longName == "LongName1"
        productDto1.price == 10

        def productDto2 = products[1]
        productDto2.name == "Product#2"
        productDto2.longName == "LongName2"
        productDto2.price == 20

    }
}
