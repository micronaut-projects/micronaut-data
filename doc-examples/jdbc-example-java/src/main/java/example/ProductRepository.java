
package example;

import io.micronaut.data.annotation.*;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;
import java.util.concurrent.CompletableFuture;

// tag::join[]
// tag::async[]
@JdbcRepository(dialect = Dialect.H2)
public interface ProductRepository extends CrudRepository<Product, Long> {
    // end::join[]
// end::async[]
    // tag::join[]
    @Join(value = "manufacturer", type = Join.Type.FETCH)
    // <1>
    List<Product> list();
    // end::join[]

    // tag::async[]
    @Join("manufacturer")
    CompletableFuture<Product> findByNameContains(String str);

    CompletableFuture<Long> countByManufacturerName(String name);

    // end::async[]
    // tag::reactive[]
    @Join("manufacturer")
    Maybe<Product> queryByNameContains(String str);

    Single<Long> countDistinctByManufacturerName(String name);
    // end::reactive[]

    // tag::native[]
    @Query("SELECT *, m_.name as m_name, m_.id as m_id FROM product p INNER JOIN manufacturer m_ ON p.manufacturer_id = m_.id WHERE p.name like :name limit 5")
    @Join(value = "manufacturer", alias = "m_")
    List<Product> searchProducts(String name);
    // end::native[]

    class Specifications {
        // tag::typesafe[]

        static PredicateSpecification<Product> manufacturerNameEquals(String name) {
            return (root, cb) -> cb.equal(root.join(Product_.manufacturer).get(Manufacturer_.name), name);
        }

        static PredicateSpecification<Product> joined() {
            return (root, cb) -> {
                root.join("manufacturer");
                return null;
            };
        }

        // end::typesafe[]
    }

// tag::join[]
// tag::async[]
}
// end::join[]
// end::async[]
