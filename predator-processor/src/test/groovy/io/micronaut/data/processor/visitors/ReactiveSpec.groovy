package io.micronaut.data.processor.visitors

import io.micronaut.data.intercept.annotation.PredatorMethod
import io.micronaut.data.intercept.reactive.*
import spock.lang.Unroll

class ReactiveSpec extends AbstractPredatorSpec {


    @Unroll
    void "test reactive method with rxjava #method"() {
        given:
        def repository = buildRepository('test.MyInterface', """

import io.micronaut.data.model.entities.Person;
import java.util.concurrent.CompletionStage;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.*;
import java.util.*;
import io.reactivex.*;

@Repository
interface MyInterface extends GenericRepository<Person, Long> {

    $returnType $method($arguments);
}
"""
        )

        expect:
        repository.findPossibleMethods(method)
                .findFirst()
                .get()
                .synthesize(PredatorMethod)
                .interceptor() == interceptor

        where:
        method         | returnType              | arguments               | interceptor
        "list"         | "Single<Page<Person>>"  | "Pageable pageable"     | FindPageReactiveInterceptor
        "list"         | "Single<Slice<Person>>" | "Pageable pageable"     | FindSliceReactiveInterceptor
        "findByName"   | "Single<Person>"        | "String name"           | FindOneReactiveInterceptor
        "findByName"   | "Flowable<Person>"      | "String name"           | FindAllReactiveInterceptor
        "find"         | "Flowable<Person>"      | "String name"           | FindAllReactiveInterceptor
        "find"         | "Single<Person>"        | "String name"           | FindOneReactiveInterceptor
        "count"        | "Single<Long>"          | "String name"           | CountReactiveInterceptor
        "countByName"  | "Single<Long>"          | "String name"           | CountReactiveInterceptor
        "delete"       | "Single<Boolean>"       | "String name"           | DeleteAllReactiveInterceptor
        "delete"       | "Single<Void>"          | "String name"           | DeleteAllReactiveInterceptor
        "deleteByName" | "Single<Boolean>"       | "String name"           | DeleteAllReactiveInterceptor
        "existsByName" | "Single<Boolean>"       | "String name"           | ExistsByReactiveInterceptor
        "findById"     | "Single<Person>"        | "Long id"               | FindByIdReactiveInterceptor
        "save"         | "Single<Person>"        | "Person person"         | SaveEntityReactiveInterceptor
        "save"         | "Single<Person>"        | "String name"           | SaveOneReactiveInterceptor
        "save"         | "Flowable<Person>"      | "List<Person> entities" | SaveAllReactiveInterceptor
        "updateByName" | "Single<Boolean>"       | "String name, int age"  | UpdateReactiveInterceptor
        "update"       | "Single<Boolean>"       | "@Id Long id, int age"  | UpdateReactiveInterceptor
    }
}
