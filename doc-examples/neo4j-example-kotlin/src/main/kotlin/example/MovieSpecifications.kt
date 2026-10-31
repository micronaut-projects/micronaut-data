package example

import io.micronaut.data.repository.jpa.criteria.PredicateSpecification

// tag::specifications[]
object MovieSpecifications {

    fun releasedAfter(year: Int) = PredicateSpecification<Movie> { root, criteriaBuilder -> // <1>
        criteriaBuilder.greaterThan(root.get("released"), year)
    }

    fun directedBy(name: String) = PredicateSpecification<Movie> { root, criteriaBuilder -> // <2>
        criteriaBuilder.equal(root.join<Movie, Person>("directors").get<String>("name"), name)
    }
}
// end::specifications[]
