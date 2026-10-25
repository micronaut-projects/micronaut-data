package example

import io.micronaut.data.age.r2dbc.annotation.AgeR2dbcRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import reactor.core.publisher.Flux

// tag::repository[]
@AgeR2dbcRepository // <1>
interface MovieRepository : ReactorCrudRepository<Movie, String> { // <2>

    fun findByDirectorsName(name: String): Flux<Movie> // <3>

    fun findByReleasedGreaterThanOrderByReleased(year: Int): Flux<Movie>
}
// end::repository[]
