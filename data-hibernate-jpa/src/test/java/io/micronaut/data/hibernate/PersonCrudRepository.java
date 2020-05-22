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

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.data.tck.repositories.PersonRepository;
import io.reactivex.Single;

import javax.transaction.Transactional;
import java.util.List;

@Repository
@Transactional
public interface PersonCrudRepository extends JpaRepository<Person, Long>, PersonRepository {
    @Query("from Person p where p.name = :n")
    List<Person> listPeople(String n);

    @Query(value = "from Person p where p.name like :n",
            countQuery = "select count(p) from Person p where p.name like :n")
    Page<Person> findPeople(String n, Pageable pageable);

    @Query("from Person p where p.name = :n")
    Person queryByName(String n);

    @Query(
            value = "SELECT u FROM Person u WHERE u.age > :age",
            countQuery = "SELECT COUNT(u) FROM Person u WHERE u.age > :age"
    )
    Single<Page<Person>> find(int age, Pageable pageable);
}
