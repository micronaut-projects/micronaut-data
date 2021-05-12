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
import io.micronaut.data.intercept.reactive.*
import spock.lang.Unroll

class ReactiveSpec extends AbstractDataSpec {


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
        "delete"       | "Completable"           | "String name"                  | DeleteAllReactiveInterceptor
        "deleteByName" | "Single<Integer>"       | "String name"                  | DeleteAllReactiveInterceptor
        "existsByName" | "Single<Boolean>"       | "String name"                  | ExistsByReactiveInterceptor
        "findById"     | "Single<Person>"        | "Long id"                      | FindByIdReactiveInterceptor
        "save"         | "Completable"           | "Person person"                | SaveEntityReactiveInterceptor
        "save"         | "Single<Long>"          | "Person person"                | SaveEntityReactiveInterceptor
        "save"         | "Single<Person>"        | "Person person"                | SaveEntityReactiveInterceptor
        "save"         | "Single<Person>"        | "String name, String publicId" | SaveOneReactiveInterceptor
        "save"         | "Flowable<Person>"      | "List<Person> entities"        | SaveAllReactiveInterceptor
        "updateByName" | "Single<Number>"        | "String name, int age"         | UpdateReactiveInterceptor
        "update"       | "Completable"           | "@Id Long id, int age"         | UpdateReactiveInterceptor
        "update"       | "Single<Number>"        | "@Id Long id, int age"         | UpdateReactiveInterceptor
        "updateAll"    | "Single<Integer>"       | "List<Person> entities"        | UpdateAllEntitiesReactiveInterceptor
        "updateAll"    | "Single<List<Person>>"  | "List<Person> entities"        | UpdateAllEntitiesReactiveInterceptor
        "updateCustom" | "Single<Integer>"       | "List<Person> entities"        | UpdateAllEntitiesReactiveInterceptor
        "updateCustom" | "Single<List<Person>>"  | "List<Person> entities"        | UpdateAllEntitiesReactiveInterceptor
        "update"       | "Single<Integer>"       | "List<Person> entities"        | UpdateAllEntitiesReactiveInterceptor
        "update"       | "Single<List<Person>>"  | "List<Person> entities"        | UpdateAllEntitiesReactiveInterceptor
        "update"       | "Completable"  | "List<Person> entities"        | UpdateAllEntitiesReactiveInterceptor
    }
}
