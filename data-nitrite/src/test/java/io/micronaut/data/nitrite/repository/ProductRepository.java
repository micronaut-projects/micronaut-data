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
