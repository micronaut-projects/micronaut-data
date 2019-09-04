package io.micronaut.data.tck.repositories;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.reactive.RxJavaCrudRepository;
import io.micronaut.data.tck.entities.Person;
import io.reactivex.*;

public interface PersonReactiveRepository extends RxJavaCrudRepository<Person, Long> {

    Single<Person> save(String name, int age);

    Single<Person> getById(Long id);

    Completable updatePerson(@Id Long id, @Parameter("name") String name);

    Flowable<Person> list(Pageable pageable);

    Single<Integer> count(String name);

    @Nullable
    Maybe<Person> findByName(String name);

    Single<Long> deleteByNameLike(String name);


    Observable<Person> findByNameLike(String name);
}
