package example

import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import reactor.core.publisher.Mono

@Singleton
open class ReactiveMovieService(private val movieRepository: ReactiveMovieRepository) {

    // tag::transactional[]
    @Transactional // <1>
    open fun saveAndCount(movie: Movie): Mono<Long> =
        movieRepository.save(movie).then(movieRepository.count()) // <2>
    // end::transactional[]
}
