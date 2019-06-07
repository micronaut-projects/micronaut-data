package io.micronaut.data.hibernate.reactive;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.Person;
import io.micronaut.data.hibernate.PersonDto;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.reactive.RxJavaCrudRepository;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;

@Repository
public interface RxJavaPersonRepo extends RxJavaCrudRepository<Person, Long> {

    Maybe<Person> findByName(String name);

    Flowable<Person> findAllByNameContains(String str);

    Single<Person> save(String name, int age);

    Single<Page<Person>> findAllByAgeBetween(int start, int end, Pageable pageable);

    Single<Integer> updateByName(String name, int age);

    Maybe<PersonDto> searchByName(String name);
}

