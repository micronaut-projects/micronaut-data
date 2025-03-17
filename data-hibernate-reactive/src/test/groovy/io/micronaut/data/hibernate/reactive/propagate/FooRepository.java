package io.micronaut.data.hibernate.reactive.propagate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;

@Repository
public interface FooRepository extends ReactorCrudRepository<Foo, Long> {}
