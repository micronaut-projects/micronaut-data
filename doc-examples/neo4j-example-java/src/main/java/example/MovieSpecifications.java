package example;

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
}
// end::specifications[]
