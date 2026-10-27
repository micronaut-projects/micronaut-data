package example

import io.micronaut.data.graph.model.criteria.GraphCriteria
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification

// tag::specifications[]
class MovieSpecifications {

    static PredicateSpecification<Movie> releasedAfter(int year) { // <1>
        { root, criteriaBuilder -> criteriaBuilder.greaterThan(root.get("released"), year) } as PredicateSpecification<Movie>
    }

    static PredicateSpecification<Movie> directedBy(String name) { // <2>
        { root, criteriaBuilder -> criteriaBuilder.equal(root.join("directors").get("name"), name) } as PredicateSpecification<Movie>
    }
// end::specifications[]

    // tag::graphCriteria[]
    static PredicateSpecification<Movie> withRoles() { // <1>
        { root, criteriaBuilder -> GraphCriteria.related(root, "roles") } as PredicateSpecification<Movie>
    }

    static PredicateSpecification<Movie> connectedTo(String title, int hops) { // <2>
        { root, criteriaBuilder -> GraphCriteria.connected(root, null, criteriaBuilder.literal(title), hops) } as PredicateSpecification<Movie>
    }
    // end::graphCriteria[]
// tag::specifications[]
}
// end::specifications[]
