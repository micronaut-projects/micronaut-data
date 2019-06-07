package io.micronaut.data.processor.visitors

import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.CountInterceptor
import io.micronaut.data.intercept.annotation.PredatorMethod
import io.micronaut.data.intercept.async.CountAsyncInterceptor
import io.micronaut.data.intercept.async.DeleteAllAsyncInterceptor
import io.micronaut.data.intercept.async.ExistsByAsyncInterceptor
import io.micronaut.data.intercept.async.FindAllAsyncInterceptor
import io.micronaut.data.intercept.async.FindByIdAsyncInterceptor
import io.micronaut.data.intercept.async.FindOneAsyncInterceptor
import io.micronaut.data.intercept.async.FindPageAsyncInterceptor
import io.micronaut.data.intercept.async.FindSliceAsyncInterceptor
import io.micronaut.data.intercept.async.SaveAllAsyncInterceptor
import io.micronaut.data.intercept.async.SaveEntityAsyncInterceptor
import io.micronaut.data.intercept.async.SaveOneAsyncInterceptor
import io.micronaut.data.intercept.async.UpdateAsyncInterceptor
import io.micronaut.data.intercept.reactive.CountReactiveInterceptor
import io.micronaut.data.model.entities.Person
import io.micronaut.inject.ExecutableMethod
import spock.lang.Unroll

class CountSpec extends AbstractPredatorSpec {

    @Unroll
    void "test count method variations for method #method"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.model.entities.*;
import java.util.concurrent.*;
import io.reactivex.*;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.*;
import java.util.*;

@Repository
interface MyInterface extends GenericRepository<Person, Long> {

    $returnType $method($arguments);
}
"""
        )

        def executableMethod = repository.findPossibleMethods(method)
                .findFirst()
                .get()
        def query = executableMethod.stringValue(Query).orElse(null)
        def ann = executableMethod
                .synthesize(PredatorMethod)

        expect:
        ann.resultType() == resultType
        ann.interceptor() == interceptor
        query == expectedQuery

        where:
        method                    | returnType     | arguments     | interceptor      | resultType | expectedQuery
        "count"                   | "Long"         | "String name" | CountInterceptor | Long       | "SELECT COUNT(person) FROM $Person.name AS person WHERE (person.name = :p1)"
        "countByName"             | "Long"         | "String name" | CountInterceptor | Long       | "SELECT COUNT(person) FROM $Person.name AS person WHERE (person.name = :p1)"
        "countDistinct"           | "Long"         | "String name" | CountInterceptor | Long       | "SELECT COUNT(person) FROM $Person.name AS person WHERE (person.name = :p1)"
        "countDistinctByName"     | "Long"         | "String name" | CountInterceptor         | Long | "SELECT COUNT(person) FROM $Person.name AS person WHERE (person.name = :p1)"
        "countDistinctName"       | "Long"         | "String name" | CountInterceptor         | Long | "SELECT COUNT(DISTINCT person.name) FROM $Person.name AS person WHERE (person.name = :p1)"
        "countDistinctNameByName" | "Long"         | "String name" | CountInterceptor         | Long | "SELECT COUNT(DISTINCT person.name) FROM $Person.name AS person WHERE (person.name = :p1)"
        "countDistinctNameByName" | "Single<Long>" | "String name" | CountReactiveInterceptor | Long | "SELECT COUNT(DISTINCT person.name) FROM $Person.name AS person WHERE (person.name = :p1)"
        "countDistinctName" | "CompletionStage<Long>" | "String name" | CountAsyncInterceptor | Long | "SELECT COUNT(DISTINCT person.name) FROM $Person.name AS person WHERE (person.name = :p1)"
    }
}
