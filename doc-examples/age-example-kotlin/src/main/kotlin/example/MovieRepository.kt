package example

import io.micronaut.data.age.annotation.AgeRepository
import io.micronaut.data.repository.CrudRepository

// tag::repository[]
@AgeRepository // <1>
interface MovieRepository : CrudRepository<Movie, String> { // <2>

    fun findByDirectorsName(name: String): List<Movie> // <3>

    fun findByReleasedGreaterThanOrderByReleased(year: Int): List<Movie>
}
// end::repository[]
