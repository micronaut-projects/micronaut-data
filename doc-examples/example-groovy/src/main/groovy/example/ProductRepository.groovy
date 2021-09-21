
package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.annotation.EntityGraph
import io.micronaut.data.jpa.repository.JpaSpecificationExecutor
import io.micronaut.data.jpa.repository.criteria.Specification
import io.micronaut.data.repository.CrudRepository
import io.reactivex.Maybe
import io.reactivex.Single

import javax.transaction.Transactional
import java.util.concurrent.CompletableFuture

// tag::join[]
// tag::async[]
// tag::specifications[]
@Repository
abstract class ProductRepository implements CrudRepository<Product, Long>, JpaSpecificationExecutor<Product> {
// end::join[]
// end::async[]
// end::specifications[]

    // tag::join[]
    @Join(value = "manufacturer", type = Join.Type.FETCH) // <1>
    abstract List<Product> list()
    // end::join[]

    // tag::entitygraph[]
    @EntityGraph(attributePaths = ["manufacturer", "title"]) // <1>
    abstract List<Product> findAll()
    // end::entitygraph[]

    // tag::async[]
    @Join("manufacturer")
    abstract CompletableFuture<Product> findByNameContains(String str)

    abstract CompletableFuture<Long> countByManufacturerName(String name)
    // end::async[]
    // tag::reactive[]
    @Join("manufacturer")
    abstract Maybe<Product> queryByNameContains(String str)

    abstract Single<Long> countDistinctByManufacturerName(String name)
    // end::reactive[]

    // tag::specifications[]

    @Transactional
    List<Product> findByName(String name, boolean caseInsensitive, boolean includeBlank) {
        Specification<Product> specification
        if (caseInsensitive) {
            specification = Specifications.nameEqualsCaseInsensitive(name)
        } else {
            specification = Specifications.nameEquals(name)
        }
        if (includeBlank) {
            specification = specification | Specifications.nameEquals("")
        }
        return findAll(specification)
    }

    // tag::spec[]
    static class Specifications {

        static Specification<Product> nameEquals(String name) {
            return (root, query, criteriaBuilder)
                    -> criteriaBuilder.equal(root.get("name"), name)
        }

        static Specification<Product> nameEqualsCaseInsensitive(String name) {
            return (root, query, criteriaBuilder)
                    -> criteriaBuilder.equal(criteriaBuilder.lower(root.get("name")), name.toLowerCase());
        }
    }
    // end::spec[]

    // end::specifications[]

// tag::join[]
// tag::async[]
// tag::specifications[]
}
// end::join[]
// end::async[]
// end::specifications[]

