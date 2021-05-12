/*
 * Copyright 2017-2020 original authors
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
import io.micronaut.data.intercept.async.*
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
@io.micronaut.context.annotation.Executable
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
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.*;
import java.util.*;

@Repository
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<Person, Long> {

    ${rowQuery ? '@Query("select name as fullName from person")' : ''}
    $returnType $method($arguments);
}

@Introspected
class FullNameDto {
    private String fullName;
    
    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    
}
"""
        )

        def ann = repository.findPossibleMethods(method)
                .findFirst()
                .get()
                .synthesize(DataMethod)

        expect:
        ann.resultType().name == resultType
        ann.interceptor() == interceptor

        where:
        method             | returnType                           | arguments                      | interceptor                | resultType         | rowQuery
        "list"             | "CompletionStage<Page<Person>>"      | "Pageable pageable"            | FindPageAsyncInterceptor   | Person.name        | false
        "listFullName"     | "CompletionStage<Page<FullNameDto>>" | "Pageable pageable"            | FindPageAsyncInterceptor   | 'test.FullNameDto' | true
        "list"             | "CompletionStage<Slice<Person>>"     | "Pageable pageable"            | FindSliceAsyncInterceptor  | Person.name        | false
        "findByName"       | "CompletionStage<Person>"            | "String name"                  | FindOneAsyncInterceptor    | Person.name        | false
        "findByName"       | "CompletionStage<List<Person>>"      | "String name"                  | FindAllAsyncInterceptor    | Person.name        | false
        "findByName"       | "CompletionStage<List<FullNameDto>>" | "String name"                  | FindAllAsyncInterceptor    | 'test.FullNameDto' | true
        "find"             | "CompletionStage<List<Person>>"      | "String name"                  | FindAllAsyncInterceptor    | Person.name        | false
        "find"             | "CompletionStage<Person>"            | "String name"                  | FindOneAsyncInterceptor    | Person.name        | false
        "count"            | "CompletionStage<Long>"              | "String name"                  | CountAsyncInterceptor      | Long.name          | false
        "countByName"      | "CompletionStage<Long>"              | "String name"                  | CountAsyncInterceptor      | Long.name          | false
        "delete"           | "CompletionStage<Integer>"           | "String name"                  | DeleteAllAsyncInterceptor  | Integer.name       | false
        "delete"           | "CompletionStage<Void>"              | "String name"                  | DeleteAllAsyncInterceptor  | Void.name          | false
        "deleteByName"     | "CompletionStage<Long>"              | "String name"                  | DeleteAllAsyncInterceptor  | void.name          | false
        "existsByName"     | "CompletionStage<Boolean>"           | "String name"                  | ExistsByAsyncInterceptor   | Boolean.name       | false
        "findById"         | "CompletionStage<Person>"            | "Long id"                      | FindByIdAsyncInterceptor   | Person.name        | false
        "findFullNameById" | "CompletionStage<FullNameDto>"       | "Long id"                      | FindOneAsyncInterceptor    | 'test.FullNameDto' | true
        "save"             | "CompletionStage<Integer>"           | "Person person"                | SaveEntityAsyncInterceptor | Integer.name       | false
        "save"             | "CompletionStage<Person>"            | "Person person"                | SaveEntityAsyncInterceptor | Person.name        | false
        "save"             | "CompletionStage<Person>"            | "String name, String publicId" | SaveOneAsyncInterceptor    | Person.name        | false
        "save"             | "CompletionStage<Integer>"           | "String name, String publicId" | SaveOneAsyncInterceptor    | Integer.name       | false
        "save"             | "CompletionStage<List<Person>>"      | "List<Person> entities"        | SaveAllAsyncInterceptor    | Person.name        | false
        "updateByName"     | "CompletionStage<Long>"              | "String name, int age"         | UpdateAsyncInterceptor     | Person.name        | false
        "update"           | "CompletionStage<Void>"              | "@Id Long id, int age"         | UpdateAsyncInterceptor     | Void.name          | false
        "updateAll"        | "CompletionStage<Integer>"           | "List<Person> entities"        | UpdateAllEntriesAsyncInterceptor | Integer.class.name | false
        "updateAll"        | "CompletionStage<List<Person>>"      | "List<Person> entities"        | UpdateAllEntriesAsyncInterceptor | Person.class.name  | false
        "updateCustom"     | "CompletionStage<Integer>"           | "List<Person> entities"        | UpdateAllEntriesAsyncInterceptor | Integer.class.name | false
        "updateCustom"     | "CompletionStage<List<Person>>"      | "List<Person> entities"        | UpdateAllEntriesAsyncInterceptor | Person.class.name  | false
        "update"           | "CompletionStage<Integer>"           | "List<Person> entities"        | UpdateAllEntriesAsyncInterceptor | Integer.class.name | false
        "update"           | "CompletionStage<List<Person>>"      | "List<Person> entities"        | UpdateAllEntriesAsyncInterceptor | Person.class.name  | false
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
@io.micronaut.context.annotation.Executable
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
@io.micronaut.context.annotation.Executable
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
