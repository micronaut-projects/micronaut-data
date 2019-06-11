package example;

import io.micronaut.data.annotation.*;
import io.micronaut.data.jpa.annotation.EntityGraph;
import io.micronaut.data.repository.CrudRepository;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;
import java.util.concurrent.CompletableFuture;

// tag::join[]
// tag::async[]
@Repository
public interface ProductRepository extends CrudRepository<Product, Long> {
// end::join[]
// end::async[]
    // tag::join[]
    Manufacturer saveManufacturer(String name);

    @Join(value = "manufacturer", type = Join.Type.FETCH) // <1>
    List<Product> list();
    // end::join[]

    // tag::entitygraph[]
    @EntityGraph(attributePaths = {"manufacturer", "title"}) // <1>
    List<Product> findAll();
    // end::entitygraph[]

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
// tag::join[]
// tag::async[]
}
// end::join[]
// end::async[]
