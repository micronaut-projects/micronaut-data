
package example;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate6.jpa.annotation.EntityGraph;
import io.micronaut.data.hibernate6.jpa.repository.JpaSpecificationExecutor;
import io.micronaut.data.hibernate6.jpa.repository.criteria.Specification;
import io.micronaut.data.repository.CrudRepository;
import io.reactivex.Maybe;
import io.reactivex.Single;

import javax.transaction.Transactional;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// tag::join[]
// tag::async[]
// tag::specifications[]
@Repository
public interface ProductRepository extends CrudRepository<Product, Long>, JpaSpecificationExecutor<Product> {
// end::join[]
// end::async[]
// end::specifications[]

    // tag::join[]
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

    // tag::specifications[]

    @Transactional
    default List<Product> findByName(String name, boolean caseInsensitive, boolean includeBlank) {
        Specification<Product> specification;
        if (caseInsensitive) {
            specification = Specifications.nameEqualsCaseInsensitive(name);
        } else {
            specification = Specifications.nameEquals(name);
        }
        if (includeBlank) {
            specification = specification.or(Specifications.nameEquals(""));
        }
        return findAll(specification);
    }

    // tag::spec[]
    class Specifications {

        public static Specification<Product> nameEquals(String name) {
            return (root, query, criteriaBuilder)
                    -> criteriaBuilder.equal(root.get("name"), name);
        }

        public static Specification<Product> nameEqualsCaseInsensitive(String name) {
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
