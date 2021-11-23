
package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.mongo.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.types.ObjectId
import reactor.core.publisher.Mono

import java.util.concurrent.CompletableFuture

// tag::join[]
// tag::async[]
@MongoRepository
public interface ProductRepository extends CrudRepository<Product, ObjectId> {
// end::join[]
// end::async[]
    // tag::join[]
    @Join("manufacturer") // <1>
    List<Product> list();
    // end::join[]

    // tag::async[]
    @Join("manufacturer")
    CompletableFuture<Product> findByNameRegex(String str);

    CompletableFuture<Long> countByManufacturerName(String name);
    // end::async[]
    // tag::reactive[]
    @Join("manufacturer")
    Mono<Product> queryByNameRegex(String str);

    Mono<Long> countDistinctByManufacturerName(String name);
    // end::reactive[]

// tag::join[]
// tag::async[]
}
// end::join[]
// end::async[]
