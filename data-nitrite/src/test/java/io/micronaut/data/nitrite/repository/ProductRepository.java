/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.Product;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Repository for testing various field types: Long ID, BigDecimal, LocalDate, UUID, Boolean. */
@NitriteRepository
public interface ProductRepository
    extends CrudRepository<Product, Long>, PageableRepository<Product, Long> {

  // Basic queries
  Optional<Product> findByName(String name);

  List<Product> findByNameContaining(String keyword);

  // Numeric comparisons with BigDecimal
  List<Product> findByPriceGreaterThan(BigDecimal price);

  List<Product> findByPriceLessThan(BigDecimal price);

  List<Product> findByPriceBetween(BigDecimal from, BigDecimal to);

  // Integer comparisons
  List<Product> findByQuantityGreaterThan(int quantity);

  List<Product> findByQuantityLessThanEquals(int quantity);

  // Negation
  List<Product> findByPriceNot(BigDecimal price);
}
