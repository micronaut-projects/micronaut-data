package io.micronaut.data.tck.repositories;

import io.micronaut.data.annotation.Fetch;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.data.tck.entities.PersonWithIdAndNameDto;
import reactor.core.publisher.Flux;

public interface StreamingPersonReactorRepository extends ReactorCrudRepository<Person, Long> {

    /**
     * Stream all Person entities.
     *
     * @return a Flux stream of Person entities
     */
    Flux<Person> list();

    /**
     * Stream a DTO projection (id, name) for all persons.
     *
     * @return a Flux stream of {@link PersonWithIdAndNameDto} objects representing the id and name of all persons
     */
    @Query("SELECT id, name FROM person")
    @Fetch(1000)
    Flux<PersonWithIdAndNameDto> listAll();
}
