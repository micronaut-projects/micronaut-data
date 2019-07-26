/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.processor.visitors

import io.micronaut.data.intercept.annotation.DataMethod
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
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.entities.PersonDto
import spock.lang.Unroll

class AsyncSpec extends AbstractDataSpec {

    void "test compile async CRUD repository"() {
        given:
        def repository = buildRepository('test.MyInterface', """

import io.micronaut.data.model.entities.Person;
import java.util.concurrent.CompletionStage;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.*;
import java.util.*;
import io.micronaut.data.repository.async.AsyncCrudRepository;

@Repository
interface MyInterface extends AsyncCrudRepository<Person, Long> {

}
"""
        )

        expect:
        repository != null
    }

    @Unroll
    void "test async method with completion stage and dto #method"() {
        given:
        def repository = buildRepository('test.MyInterface', """

import io.micronaut.data.model.entities.*;
import java.util.concurrent.CompletionStage;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.*;
import java.util.*;

@Repository
interface MyInterface extends GenericRepository<Person, Long> {

    $returnType $method($arguments);
}
"""
        )

        def execMethod = repository.findPossibleMethods(method)
                .findFirst()
                .get()
        def ann = execMethod
                .synthesize(DataMethod)

        expect:
        ann.resultType() == resultType
        ann.interceptor() == interceptor
        execMethod.isTrue(DataMethod, DataMethod.META_MEMBER_DTO)

        where:
        method    | returnType                         | arguments     | interceptor             | resultType
        "find"    | "CompletionStage<PersonDto>"       | "String name" | FindOneAsyncInterceptor | PersonDto
        "findAll" | "CompletionStage<List<PersonDto>>" | "String name" | FindAllAsyncInterceptor | PersonDto
    }

    @Unroll
    void "test async method with completion stage #method"() {
        given:
        def repository = buildRepository('test.MyInterface', """

import io.micronaut.data.model.entities.Person;
import java.util.concurrent.CompletionStage;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.*;
import java.util.*;

@Repository
interface MyInterface extends GenericRepository<Person, Long> {

    $returnType $method($arguments);
}
"""
        )

        def ann = repository.findPossibleMethods(method)
                .findFirst()
                .get()
                .synthesize(DataMethod)

        expect:
        ann.resultType() == resultType
        ann.interceptor() == interceptor

        where:
        method         | returnType                       | arguments               | interceptor                | resultType
        "list"         | "CompletionStage<Page<Person>>"  | "Pageable pageable"     | FindPageAsyncInterceptor   | Person
        "list"         | "CompletionStage<Slice<Person>>" | "Pageable pageable"     | FindSliceAsyncInterceptor  | Person
        "findByName"   | "CompletionStage<Person>"        | "String name"           | FindOneAsyncInterceptor    | Person
        "findByName"   | "CompletionStage<List<Person>>"  | "String name"           | FindAllAsyncInterceptor    | Person
        "find"         | "CompletionStage<List<Person>>"  | "String name"           | FindAllAsyncInterceptor    | Person
        "find"         | "CompletionStage<Person>"        | "String name"           | FindOneAsyncInterceptor    | Person
        "count"        | "CompletionStage<Long>"          | "String name"           | CountAsyncInterceptor      | Long
        "countByName"  | "CompletionStage<Long>"          | "String name"           | CountAsyncInterceptor      | Long
        "delete"       | "CompletionStage<Integer>"       | "String name"           | DeleteAllAsyncInterceptor  | void.class
        "delete"       | "CompletionStage<Void>"          | "String name"           | DeleteAllAsyncInterceptor  | void.class
        "deleteByName" | "CompletionStage<Long>"          | "String name"           | DeleteAllAsyncInterceptor  | void.class
        "existsByName" | "CompletionStage<Boolean>"       | "String name"           | ExistsByAsyncInterceptor   | Boolean
        "findById"     | "CompletionStage<Person>"        | "Long id"               | FindByIdAsyncInterceptor   | Person
        "save"         | "CompletionStage<Person>"        | "Person person"         | SaveEntityAsyncInterceptor | Person
        "save"         | "CompletionStage<Person>"        | "String name"           | SaveOneAsyncInterceptor    | Person
        "save"         | "CompletionStage<List<Person>>"  | "List<Person> entities" | SaveAllAsyncInterceptor    | void.class
        "updateByName" | "CompletionStage<Long>"          | "String name, int age"  | UpdateAsyncInterceptor     | Person
        "update"       | "CompletionStage<Void>"          | "@Id Long id, int age"  | UpdateAsyncInterceptor     | Void
    }

    @Unroll
    void "test async method with completable future #method"() {
        given:
        def repository = buildRepository('test.MyInterface', """

import io.micronaut.data.model.entities.Person;
import java.util.concurrent.*;
import io.micronaut.data.model.*;
import java.util.*;

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
                .synthesize(DataMethod)
                .interceptor() == interceptor

        where:
        method         | returnType                         | arguments           | interceptor
        "list"         | "CompletableFuture<Page<Person>>"  | "Pageable pageable" | FindPageAsyncInterceptor
        "list"         | "CompletableFuture<Slice<Person>>" | "Pageable pageable" | FindSliceAsyncInterceptor
        "findByName"   | "CompletableFuture<Person>"        | "String name"       | FindOneAsyncInterceptor
        "findByName"   | "CompletableFuture<List<Person>>"  | "String name"       | FindAllAsyncInterceptor
        "find"         | "CompletableFuture<List<Person>>"  | "String name"       | FindAllAsyncInterceptor
        "find"         | "CompletableFuture<Person>"        | "String name"       | FindOneAsyncInterceptor
        "count"        | "CompletableFuture<Long>"          | "String name"       | CountAsyncInterceptor
        "delete"       | "CompletableFuture<Long>"          | "String name"       | DeleteAllAsyncInterceptor
        "delete"       | "CompletableFuture<Void>"          | "String name"       | DeleteAllAsyncInterceptor
        "deleteByName" | "CompletableFuture<Long>"          | "String name"       | DeleteAllAsyncInterceptor
        "existsByName" | "CompletableFuture<Boolean>"       | "String name"       | ExistsByAsyncInterceptor
        "findById"     | "CompletableFuture<Person>"        | "Long id"           | FindByIdAsyncInterceptor
    }

    @Unroll
    void "test async method with future #method"() {
        given:
        def repository = buildRepository('test.MyInterface', """

import io.micronaut.data.model.entities.Person;
import java.util.concurrent.*;
import io.micronaut.data.model.*;
import java.util.*;

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
                .synthesize(DataMethod)
                .interceptor() == interceptor

        where:
        method         | returnType              | arguments           | interceptor
        "list"         | "Future<Page<Person>>"  | "Pageable pageable" | FindPageAsyncInterceptor
        "list"         | "Future<Slice<Person>>" | "Pageable pageable" | FindSliceAsyncInterceptor
        "findByName"   | "Future<Person>"        | "String name"       | FindOneAsyncInterceptor
        "findByName"   | "Future<List<Person>>"  | "String name"       | FindAllAsyncInterceptor
        "find"         | "Future<List<Person>>"  | "String name"       | FindAllAsyncInterceptor
        "find"         | "Future<Person>"        | "String name"       | FindOneAsyncInterceptor
        "count"        | "Future<Long>"          | "String name"       | CountAsyncInterceptor
        "delete"       | "Future<Integer>"       | "String name"       | DeleteAllAsyncInterceptor
        "delete"       | "Future<Void>"          | "String name"       | DeleteAllAsyncInterceptor
        "deleteByName" | "Future<Long>"          | "String name"       | DeleteAllAsyncInterceptor
        "existsByName" | "Future<Boolean>"       | "String name"       | ExistsByAsyncInterceptor
        "findById"     | "Future<Person>"        | "Long id"           | FindByIdAsyncInterceptor
    }
}
