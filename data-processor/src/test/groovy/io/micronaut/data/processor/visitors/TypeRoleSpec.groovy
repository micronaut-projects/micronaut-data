package io.micronaut.data.processor.visitors

import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.intercept.reactive.CountReactiveInterceptor
import io.micronaut.data.intercept.reactive.DeleteAllReactiveInterceptor
import io.micronaut.data.intercept.reactive.ExistsByReactiveInterceptor
import io.micronaut.data.intercept.reactive.FindAllReactiveInterceptor
import io.micronaut.data.intercept.reactive.FindByIdReactiveInterceptor
import io.micronaut.data.intercept.reactive.FindOneReactiveInterceptor
import io.micronaut.data.intercept.reactive.FindPageReactiveInterceptor
import io.micronaut.data.intercept.reactive.FindSliceReactiveInterceptor
import io.micronaut.data.intercept.reactive.SaveAllReactiveInterceptor
import io.micronaut.data.intercept.reactive.SaveEntityReactiveInterceptor
import io.micronaut.data.intercept.reactive.SaveOneReactiveInterceptor
import io.micronaut.data.intercept.reactive.UpdateEntityReactiveInterceptor
import io.micronaut.data.intercept.reactive.UpdateReactiveInterceptor
import spock.lang.Unroll

class TypeRoleSpec extends AbstractDataSpec {
    @Unroll
    void "test repository with addition type role as parameter #method"() {
        given:
        def repository = buildRepository('test.MyInterface', """

import io.micronaut.data.model.entities.Person;
import java.util.concurrent.CompletionStage;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.*;
import java.util.*;
import io.reactivex.*;
import java.sql.Connection;

@Repository
@RepositoryConfiguration(
    typeRoles = @TypeRole(
            role = "connection",
            type = java.sql.Connection.class
    )
)
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<Person, Long> {

    $returnType $method($arguments, Connection connection);
}
"""
        )

        expect:
        repository.findPossibleMethods(method)
                .findFirst()
                .get()
                .synthesize(DataMethod)
                .interceptor() == interceptor

        where:
        method         | returnType              | arguments                      | interceptor
        "list"         | "Single<Page<Person>>"  | "Pageable pageable"            | FindPageReactiveInterceptor
        "list"         | "Single<Slice<Person>>" | "Pageable pageable"            | FindSliceReactiveInterceptor
        "findByName"   | "Single<Person>"        | "String name"                  | FindOneReactiveInterceptor
        "findByName"   | "Flowable<Person>"      | "String name"                  | FindAllReactiveInterceptor
        "find"         | "Flowable<Person>"      | "String name"                  | FindAllReactiveInterceptor
        "find"         | "Single<Person>"        | "String name"                  | FindOneReactiveInterceptor
        "count"        | "Single<Long>"          | "String name"                  | CountReactiveInterceptor
        "countByName"  | "Single<Long>"          | "String name"                  | CountReactiveInterceptor
        "delete"       | "Single<Long>"          | "String name"                  | DeleteAllReactiveInterceptor
        "delete"       | "Single<Void>"          | "String name"                  | DeleteAllReactiveInterceptor
        "deleteByName" | "Single<Integer>"       | "String name"                  | DeleteAllReactiveInterceptor
        "existsByName" | "Single<Boolean>"       | "String name"                  | ExistsByReactiveInterceptor
        "findById"     | "Single<Person>"        | "Long id"                      | FindByIdReactiveInterceptor
        "save"         | "Single<Person>"        | "Person person"                | SaveEntityReactiveInterceptor
        "save"         | "Single<Person>"        | "String name, String publicId" | SaveOneReactiveInterceptor
        "save"         | "Flowable<Person>"      | "List<Person> entities"        | SaveAllReactiveInterceptor
        "updateByName" | "Single<Number>"        | "String name, int age"         | UpdateReactiveInterceptor
        "update"       | "Completable"           | "@Id Long id, int age"         | UpdateReactiveInterceptor
        "update"       | "Single<Person>"        | "Person person"                | UpdateEntityReactiveInterceptor
    }
}
