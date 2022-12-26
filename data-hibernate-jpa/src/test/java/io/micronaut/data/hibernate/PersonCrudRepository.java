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
package io.micronaut.data.hibernate;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.QueryHint;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.data.tck.repositories.PersonRepository;
import org.reactivestreams.Publisher;

import javax.transaction.Transactional;
import java.util.List;

@Repository
public interface PersonCrudRepository extends JpaRepository<Person, Long>, PersonRepository {

    @Override
    Person save(@Parameter("name") String name, @Parameter("age") int age);

    @Override
    int saveCustom(@Parameter("xyz") String xyz, @Parameter("age") int age);

    @Query("from Person p where p.name = :n")
    @Transactional
    List<Person> listPeople(String n);

    @Query(value = "from Person p where p.name like :n",
            countQuery = "select count(p) from Person p where p.name like :n")
    @Transactional
    Page<Person> findPeople(String n, Pageable pageable);

    @Query("from Person p where p.name = :n")
    @Transactional
    Person queryByName(String n);

    @Query(
            value = "SELECT u FROM Person u WHERE u.age > :age",
            countQuery = "SELECT COUNT(u) FROM Person u WHERE u.age > :age"
    )
    Publisher<Page<Person>> find(int age, Pageable pageable);

    @Query("UPDATE Person p SET p.enabled = false WHERE p.id = :id")
    Publisher<Long> updatePersonRx(Long id);

    @Override
    @QueryHint(name = "javax.persistence.FlushModeType", value = "AUTO")
    void updatePerson(@Id Long id, @Parameter("name") String name);

    @Override
    @QueryHint(name = "javax.persistence.FlushModeType", value = "AUTO")
    Long updatePerson(@Id Long id, int age);
}
