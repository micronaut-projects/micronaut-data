
package example;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.reactive.repository.jpa.ReactorJpaSpecificationExecutor;
import io.micronaut.data.jpa.annotation.EntityGraph;
import io.micronaut.data.jpa.repository.criteria.Specification;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;

import javax.transaction.Transactional;

// tag::join[]
// tag::async[]
// tag::specifications[]
@Repository
public interface ProductRepository extends ReactorCrudRepository<Product, Long>, ReactorJpaSpecificationExecutor<Product> {
// end::join[]
// end::async[]
// end::specifications[]

    // tag::join[]
    @Join(value = "manufacturer", type = Join.Type.FETCH) // <1>
    Flux<Product> list();
    // end::join[]

    // tag::entitygraph[]
    @EntityGraph(attributePaths = {"manufacturer", "title"}) // <1>
    Flux<Product> findAll();
    // end::entitygraph[]

    // tag::specifications[]
    @Transactional
    default Flux<Product> findByName(String name, boolean caseInsensitive, boolean includeBlank) {
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
