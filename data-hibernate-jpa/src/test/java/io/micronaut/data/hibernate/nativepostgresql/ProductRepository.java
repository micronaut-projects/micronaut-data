package io.micronaut.data.hibernate.nativepostgresql;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

@Requires(property = "spec.name", value = "nativepostgresql")
@Repository
interface ProductRepository extends JpaRepository<Product, Long> {
    default void createProductIfNotExists(Product product) {
        createProductIfNotExists(product.getId(), product.getCode(), product.getName());
    }

    @Query(
        value = "INSERT INTO products(id, code, name) VALUES(:id, :code, :name) ON CONFLICT DO NOTHING",
        nativeQuery = true
    )
    void createProductIfNotExists(Long id, String code, String name);
}
