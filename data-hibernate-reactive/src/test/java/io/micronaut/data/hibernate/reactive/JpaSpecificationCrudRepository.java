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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.reactive.repository.jpa.ReactorJpaSpecificationExecutor;
import io.micronaut.data.jpa.repository.criteria.Specification;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import io.micronaut.data.tck.entities.Person;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface JpaSpecificationCrudRepository extends ReactorCrudRepository<Person, Long>, ReactorJpaSpecificationExecutor<Person> {

    Mono<Page<Person>> queryAll(Pageable pageable);

    Mono<Person> get(Long id);

    Mono<Void> updatePerson(@Id Long id, String name);

    Flux<Person> list(Pageable pageable);

    Mono<Integer> count(String name);

    @Nullable
    Mono<Person> findByName(String name);

    Flux<Person> findByNameLike(String name);

    @Query("from Person p where p.name = :n")
    Flux<Person> listPeople(String n);

    @Query("from Person p where p.name = :n")
    Mono<Person> queryByName(String n);

    Mono<Integer> findAgeByName(String name);

    Mono<Integer> findMaxAgeByNameLike(String name);

    Mono<Integer> findMinAgeByNameLike(String name);

    Mono<Integer> getSumAgeByNameLike(String name);

    Mono<Long> getAvgAgeByNameLike(String name);

    Flux<Integer> readAgeByNameLike(String name);

    Flux<Person> findByNameLikeOrderByAge(String name);

    Flux<Person> findByNameLikeOrderByAgeDesc(String name);

    class Specifications {
        public static Specification<Person> ageGreaterThanThirty() {
            return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThan(
                    root.get("age"), 30
            );
        }

        public static Specification<Person> nameEquals(String name) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                    root.get("name"), name
            );
        }
    }
}
