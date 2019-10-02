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
package io.micronaut.data.tck.repositories;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Slice;
import io.micronaut.data.model.Sort;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;
import io.micronaut.data.tck.entities.Person;

import java.util.List;
import java.util.Optional;

public interface PersonRepository extends CrudRepository<Person, Long>, PageableRepository<Person, Long> {

    int countByAgeGreaterThan(Integer wrapper);

    int countByAgeLessThan(int wrapper);

    Person save(String name, int age);

    Person get(Long id);

    void updatePerson(@Id Long id, @Parameter("name") String name);

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
}
