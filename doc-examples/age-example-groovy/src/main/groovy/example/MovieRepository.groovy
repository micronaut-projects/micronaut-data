package example

import io.micronaut.data.age.annotation.AgeRepository
import io.micronaut.data.repository.CrudRepository

// tag::repository[]
@AgeRepository // <1>
interface MovieRepository extends CrudRepository<Movie, String> { // <2>

    List<Movie> findByDirectorsName(String name) // <3>

    List<Movie> findByReleasedGreaterThanOrderByReleased(int year)
}
// end::repository[]
