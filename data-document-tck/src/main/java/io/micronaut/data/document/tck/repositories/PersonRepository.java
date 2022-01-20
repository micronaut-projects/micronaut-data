/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.document.tck.repositories;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.document.tck.entities.Person;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Slice;
import io.micronaut.data.model.Sort;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;

import java.util.List;

public interface PersonRepository extends CrudRepository<Person, String>, PageableRepository<Person, String>, JpaSpecificationExecutor<Person> {

    List<Person> listTop10(Sort sort);

    Person get(String id);

    int count(String name);

    Person save(@Parameter("name") String name, @Parameter("age") int age);

    List<Person> list(Pageable pageable);

    long updateAll(List<Person> people);

    List<Person> updatePeople(List<Person> people);

    @Nullable
    Person findByName(String name);

    void updatePerson(@Id String id, @Parameter("name") String name);

    long updateByName(String name, int age);

    List<Person> findByNameRegex(String name);

    List<Person> findByNameRegex(String name, Pageable pageable);

    io.micronaut.data.model.Page<Person> getByNameRegex(String name, Pageable pageable);

    io.micronaut.data.model.Page<Person> findAllByNameRegex(String name, Pageable pageable);

    Slice<Person> queryByNameRegex(String name, Pageable pageable);

    Long deleteByNameRegex(String name);

    long updatePersonCount(@Id String id, @Parameter("name") String name);

    int countByAgeGreaterThan(Integer wrapper);

    int countByAgeLessThan(int wrapper);

    int findAgeByName(String name);

    List<Person> findAllByNameRegex(String name);

    int findMaxAgeByNameRegex(String name);

    int findMinAgeByNameRegex(String name);

    int getSumAgeByNameRegex(String name);

    long getAvgAgeByNameRegex(String name);

    List<Integer> readAgeByNameRegex(String name);

    List<Person> findByNameRegexOrderByAge(String name);

    List<Person> findByNameRegexOrderByAgeDesc(String name);

    class Specifications {

        public static PredicateSpecification<Person> nameEquals(String name) {
            return (root, criteriaBuilder) -> criteriaBuilder.equal(root.get("name"), name);
        }

        public static PredicateSpecification<Person> nameEqualsCaseInsensitive(String name) {
            return (root, criteriaBuilder) -> criteriaBuilder.equal(criteriaBuilder.lower(root.get("name")), name.toLowerCase());
        }
    }
}
