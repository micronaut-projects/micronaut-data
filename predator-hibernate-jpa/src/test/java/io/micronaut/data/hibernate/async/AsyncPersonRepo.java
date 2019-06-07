package io.micronaut.data.hibernate.async;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.Person;
import io.micronaut.data.repository.async.AsyncCrudRepository;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Repository
public interface AsyncPersonRepo extends AsyncCrudRepository<Person, Long> {

    CompletableFuture<Person> findByName(String name);

    CompletableFuture<List<Person>> findAllByNameContains(String str);

    CompletableFuture<Person> save(String name, int age);
}
