package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.math.BigDecimal;
import java.util.UUID;

/** Entity for testing various field types and ID strategies. */
@MappedEntity("products")
public class Product {
  @Id @GeneratedValue private Long id;

  private String name;
  private BigDecimal price;
  private Integer quantity;
  private Boolean available;
  private UUID sku;

  public Product() {}

  public Product(String name, BigDecimal price, Integer quantity) {
    this.name = name;
    this.price = price;
    this.quantity = quantity;
    this.available = true;
    this.sku = UUID.randomUUID();
  }

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

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public void setQuantity(Integer quantity) {
    this.quantity = quantity;
  }

  public Boolean isAvailable() {
    return available;
  }

  public void setAvailable(Boolean available) {
    this.available = available;
  }

  public UUID getSku() {
    return sku;
  }

  public void setSku(UUID sku) {
    this.sku = sku;
  }
}
