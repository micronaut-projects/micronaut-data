package example

import io.micronaut.data.graph.model.criteria.GraphCriteria
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification

// tag::specifications[]
object MovieSpecifications {

    fun releasedAfter(year: Int) = PredicateSpecification<Movie> { root, criteriaBuilder -> // <1>
        criteriaBuilder.greaterThan(root.get("released"), year)
    }

    fun directedBy(name: String) = PredicateSpecification<Movie> { root, criteriaBuilder -> // <2>
        criteriaBuilder.equal(root.join<Movie, Person>("directors").get<String>("name"), name)
    }
// end::specifications[]

    // tag::graphCriteria[]
    fun withRoles() = PredicateSpecification<Movie> { root, _ -> // <1>
        GraphCriteria.related(root, "roles")
    }

    fun connectedTo(title: String, hops: Int) = PredicateSpecification<Movie> { root, criteriaBuilder -> // <2>
        GraphCriteria.connected(root, null, criteriaBuilder.literal(title), hops)
    }
    // end::graphCriteria[]
// tag::specifications[]
}
// end::specifications[]
