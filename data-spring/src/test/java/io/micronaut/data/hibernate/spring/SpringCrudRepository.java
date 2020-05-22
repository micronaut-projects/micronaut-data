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
package io.micronaut.data.hibernate.spring;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.tck.entities.Person;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;

import javax.transaction.Transactional;
import java.util.List;

@Repository
@Transactional
public interface SpringCrudRepository extends CrudRepository<Person, Long>, JpaSpecificationExecutor<Person> {

    Page<Person> queryAll(Pageable pageable);

    Person get(Long id);

    void updatePerson(@Id Long id, String name);

    List<Person> list(Pageable pageable);

    int count(String name);

    @Nullable
    Person findByName(String name);

    List<Person> findByNameLike(String name);

    @Query("from Person p where p.name = :n")
    List<Person> listPeople(String n);

    @Query("from Person p where p.name = :n")
    Person queryByName(String n);

    int findAgeByName(String name);

    int findMaxAgeByNameLike(String name);

    int findMinAgeByNameLike(String name);

    int getSumAgeByNameLike(String name);

    long getAvgAgeByNameLike(String name);

    List<Integer> readAgeByNameLike(String name);

    List<Person> findByNameLikeOrderByAge(String name);

    List<Person> findByNameLikeOrderByAgeDesc(String name);


    class Specifications {
        public static Specification<Person> ageGreaterThanThirty() {
            return (Specification<Person>) (root, query, criteriaBuilder) -> criteriaBuilder.greaterThan(
                    root.get("age"), 30
            );
        }

        public static Specification<Person> nameEquals(String name) {
            return (Specification<Person>) (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                    root.get("name"), name
            );
        }
    }
}
