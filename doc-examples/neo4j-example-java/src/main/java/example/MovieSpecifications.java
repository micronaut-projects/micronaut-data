package example;

import io.micronaut.data.graph.model.criteria.GraphCriteria;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;

// tag::specifications[]
final class MovieSpecifications {

    private MovieSpecifications() {
    }

    static PredicateSpecification<Movie> releasedAfter(int year) { // <1>
        return (root, criteriaBuilder) -> criteriaBuilder.greaterThan(root.get("released"), year);
    }

    static PredicateSpecification<Movie> directedBy(String name) { // <2>
        return (root, criteriaBuilder) -> criteriaBuilder.equal(root.join("directors").get("name"), name);
    }
// end::specifications[]

    // tag::graphCriteria[]
    static PredicateSpecification<Movie> withRoles() { // <1>
        return (root, criteriaBuilder) -> GraphCriteria.related(root, "roles");
    }

    static PredicateSpecification<Movie> connectedTo(String title, int hops) { // <2>
        return (root, criteriaBuilder) -> GraphCriteria.connected(root, null, criteriaBuilder.literal(title), hops);
    }
    // end::graphCriteria[]
// tag::specifications[]
}
// end::specifications[]
