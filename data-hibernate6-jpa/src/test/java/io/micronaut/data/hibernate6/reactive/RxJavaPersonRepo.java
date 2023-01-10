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
package io.micronaut.data.hibernate6.reactive;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.data.tck.entities.PersonDto;
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

