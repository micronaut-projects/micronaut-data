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
package io.micronaut.data.hibernate.reactive;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.data.tck.repositories.PersonReactiveRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.transaction.Transactional;
import java.util.List;

@Repository
public interface PersonCrudRepository extends PersonReactiveRepository {

    @Transactional
    default Mono<List<Person>> findAllAndDelete(String like) {
        return findByNameLike(like)
                .collectList()
                .flatMap(list -> deleteAll(list).thenReturn(list));
    }

    @Transactional
    default Mono<Person> findOneAndDelete(String name) {
        return findByName(name).flatMap(person -> delete(person).thenReturn(person));
    }

    @Override
    Mono<Person> save(@Parameter("name") String name, @Parameter("age") int age);

//    Mono<Integer> saveCustom(@Parameter("xyz") String xyz, @Parameter("age") int age);

    @Query("from Person p where p.name = :n")
    @Transactional
    Flux<Person> listPeople(String n);

    @Query(value = "from Person p where p.name like :n",
            countQuery = "select count(p) from Person p where p.name like :n")
    @Transactional
    Mono<Page<Person>> findPeople(String n, Pageable pageable);

    @Query("from Person p where p.name = :n")
    @Transactional
    Mono<Person> findByName(String n);

    @Query(
            value = "SELECT u FROM Person u WHERE u.age > :age",
            countQuery = "SELECT COUNT(u) FROM Person u WHERE u.age > :age"
    )
    Mono<Page<Person>> find(int age, Pageable pageable);

    @Query("UPDATE Person p SET p.enabled = false WHERE p.id = :id")
    Mono<Long> updatePersonRx(Long id);

    Mono<Integer> findAgeByName(String name);

    Mono<Integer> findMaxAgeByNameLike(String name);

    Mono<Integer> findMinAgeByNameLike(String name);

    Mono<Integer> getSumAgeByNameLike(String name);

    Mono<Long> getAvgAgeByNameLike(String name);

    Flux<Integer> readAgeByNameLike(String name);

    Flux<Person> findByNameLikeOrderByAge(String name);

    Flux<Person> findByNameLikeOrderByAgeDesc(String name);
}
