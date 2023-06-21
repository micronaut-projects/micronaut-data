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
import io.micronaut.data.intercept.reactive.UpdateReactiveInterceptor
import io.micronaut.data.model.DataType
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import spock.lang.IgnoreIf
import spock.lang.Unroll

@IgnoreIf({ !jvm.isJava14Compatible() })
class RecordsSpec extends AbstractDataSpec {

    void 'test build create table'() {
        given:
        def entity = buildEntity('test.Person', '''
import io.micronaut.data.annotation.*;
record Person(@Id @GeneratedValue @io.micronaut.core.annotation.Nullable Long id, String name, int age) {}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ANSI)
        def sql = builder.buildBatchCreateTableStatement(entity)

        expect:
        sql == 'CREATE TABLE "person" ("id" BIGINT PRIMARY KEY AUTO_INCREMENT,"name" VARCHAR(255) NOT NULL,"age" INT NOT NULL);'

    }


    void 'test runtime persistent entity'() {
        given:
        def introspection = buildBeanIntrospection('test.Person', '''
package test;
import io.micronaut.data.annotation.*;

@io.micronaut.data.annotation.MappedEntity
record Person(@Id @GeneratedValue @io.micronaut.core.annotation.Nullable Long id, String name, int age) {}
''')
        def entity = PersistentEntity.of(introspection)
        def property = entity.getPropertyByName("name")
        expect:
        property.dataType == DataType.STRING

    }

    @Unroll
    void "test reactive method with rxjava and record #method"() {
        given:
        def repository = buildRepository('test.MyInterface', """

import java.util.concurrent.CompletionStage;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.*;
import java.util.*;
import io.reactivex.*;

@Repository
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<Person, Long> {

    $returnType $method($arguments);
}

@io.micronaut.data.annotation.MappedEntity
record Person(@Id @GeneratedValue @io.micronaut.core.annotation.Nullable Long id, String name, int age) {}
"""
        )

        expect:
        repository.findPossibleMethods(method)
                .findFirst()
                .get()
                .synthesize(DataMethod)
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
        "delete"       | "Single<Long>"          | "String name"           | DeleteAllReactiveInterceptor
        "delete"       | "Single<Void>"          | "String name"           | DeleteAllReactiveInterceptor
        "deleteByName" | "Single<Integer>"       | "String name"           | DeleteAllReactiveInterceptor
        "existsByName" | "Single<Boolean>"       | "String name"           | ExistsByReactiveInterceptor
        "findById"     | "Single<Person>"        | "Long id"               | FindByIdReactiveInterceptor
        "save"         | "Single<Person>"        | "Person person"         | SaveEntityReactiveInterceptor
        "save"         | "Single<Person>"        | "String name, int age"  | SaveOneReactiveInterceptor
        "save"         | "Flowable<Person>"      | "List<Person> entities" | SaveAllReactiveInterceptor
        "updateByName" | "Single<Number>"       | "String name, int age"  | UpdateReactiveInterceptor
        "update"       | "Completable"       | "@Id Long id, int age"  | UpdateReactiveInterceptor
    }
}
