package example

import io.micronaut.data.repository.jpa.criteria.PredicateSpecification

// tag::specifications[]
class MovieSpecifications {

    static PredicateSpecification<Movie> releasedAfter(int year) { // <1>
        { root, criteriaBuilder -> criteriaBuilder.greaterThan(root.get("released"), year) } as PredicateSpecification<Movie>
    }

    static PredicateSpecification<Movie> directedBy(String name) { // <2>
        { root, criteriaBuilder -> criteriaBuilder.equal(root.join("directors").get("name"), name) } as PredicateSpecification<Movie>
    }
}
// end::specifications[]
