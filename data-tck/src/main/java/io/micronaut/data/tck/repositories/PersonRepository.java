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
package io.micronaut.data.tck.repositories;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Slice;
import io.micronaut.data.model.Sort;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.PageableRepository;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.data.tck.entities.TotalDto;
import io.reactivex.Single;
import jakarta.persistence.criteria.JoinType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public interface PersonRepository extends CrudRepository<Person, Long>, PageableRepository<Person, Long>, JpaSpecificationExecutor<Person> {

    @Query("select count(*) as total from person")
    TotalDto getTotal();

    int countByAgeGreaterThan(Integer wrapper);

    int countByAgeLessThan(int wrapper);

    Person save(@Parameter("name") String name, @Parameter("age") int age);

    @Query("INSERT INTO person(name, age, enabled) VALUES (:xyz, :age, TRUE)")
    int saveCustom(@Parameter("xyz") String xyz, @Parameter("age") int age);

    Person get(Long id);

    void updatePerson(@Id Long id, @Parameter("name") String name);

    long updatePersonCount(@Id Long id, @Parameter("name") String name);

    Single<Long> updatePersonRx(@Id Long id, @Parameter("name") String name);

    @Query("UPDATE person SET name = 'test' WHERE id = :id")
    Single<Long> updatePersonCustomRx(Long id);

    @Query("UPDATE person SET name = 'test' WHERE id = :xyz")
    Future<Long> updatePersonCustomFuture(Long xyz);

    @Query("UPDATE person SET name = 'test' WHERE id = :xyz")
    long updatePersonCustom(Long xyz);

    @Query("SELECT * FROM person WHERE name = :names1 or name IN(:names3) or name IN(:names0) or (:name4 = name)")
    List<Person> queryNames(List<String> names0, String names1, List<String> names2, List<String> names3, String name4);

    CompletableFuture<Long> updatePersonFuture(@Id Long id, @Parameter("name") String name);

    long updateByName(String name, int age);

    List<Person> list(Pageable pageable);

    int count(String name);

    @Nullable
    Person findByName(String name);

    Long deleteByNameLike(String name);

    Person getByName(String name);

    List<Person> findByNameLike(String name);

    int findAgeByName(String name);

    int findMaxAgeByNameLike(String name);

    int findMinAgeByNameLike(String name);

    int getSumAgeByNameLike(String name);

    long getAvgAgeByNameLike(String name);

    List<Integer> readAgeByNameLike(String name);

    List<Person> findByNameLikeOrderByAge(String name);

    List<Person> findByNameLikeOrderByAgeDesc(String name);

    Page<Person> findByNameLike(String name, Pageable pageable);

    List<Person> listTop10(Sort sort);

    Slice<Person> find(Pageable pageable);

    Slice<Person> queryByNameLike(String name, Pageable pageable);

    Optional<Person> findOptionalByName(String name);

    List<Person> findAllByName(String name);

    List<Person> findAllByNameLike(String name, Pageable pageable);

    @Query(value = "select * from person person_ where person_.name like :n",
            countQuery = "select count(*) from person person_ where person_.name like :n")
    Page<Person> findPeople(String n, Pageable pageable);

    long updateAll(List<Person> people);

    List<Person> updatePeople(List<Person> people);

    @Query("UPDATE person SET name = :newName WHERE (name = :oldName)")
    long updateNamesCustom(String newName, String oldName);

    @Query("UPDATE person SET name = :name WHERE id = :id")
    long updateCustomOnlyNames(List<Person> people);

    @Query("INSERT INTO person(name, age, enabled) VALUES (:name, :age, TRUE)")
    int saveCustom(List<Person> people);

    @Query("INSERT INTO person(name, age, enabled) VALUES (:name, :age, TRUE)")
    int saveCustomSingle(Person people);

    @Query("DELETE FROM person WHERE name = :name")
    int deleteCustom(List<Person> people);

    @Query("DELETE FROM person WHERE name = :name")
    int deleteCustomSingle(Person person);

    @Query("DELETE FROM person WHERE name = :xyz")
    int deleteCustomSingleNoEntity(String xyz);

    List<Person> findAllByAgeInRange(int from, int to);

    Person updateByNameAndAge(String name, int age, Person person);

    Long updatePerson(@Id Long id, @Parameter("age") int age);

    class Specifications {

        public static PredicateSpecification<Person> nameEquals(String name) {
            return (root, criteriaBuilder) -> criteriaBuilder.equal(root.get("name"), name);
        }

        public static PredicateSpecification<Person> nameEqualsCaseInsensitive(String name) {
            return (root, criteriaBuilder) -> criteriaBuilder.equal(criteriaBuilder.lower(root.get("name")), name.toLowerCase());
        }

        public static PredicateSpecification<Person> idsIn(Long... ids) {
            return (root, criteriaBuilder) -> root.get("id").in(Arrays.asList(ids));
        }
    }
}
