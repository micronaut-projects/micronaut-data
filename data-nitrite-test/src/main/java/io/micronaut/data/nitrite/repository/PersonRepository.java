/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.nitrite.repository;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.Person;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;
import java.util.List;
import java.util.Optional;

@NitriteRepository
public interface PersonRepository
    extends CrudRepository<Person, String>, PageableRepository<Person, String> {
  Optional<Person> findByName(String name);

  List<Person> findByAgeGreaterThan(int age);

  List<Person> findByNameContaining(String keyword);

  // Comparison operators
  List<Person> findByAgeLessThan(int age);

  List<Person> findByAgeLessThanEquals(int age);

  List<Person> findByAgeGreaterThanEquals(int age);

  // Pattern matching
  List<Person> findByNameStartsWith(String prefix);

  List<Person> findByNameEndsWith(String suffix);

  // Null/Empty checks
  List<Person> findByNameIsNull();

  List<Person> findByNameIsNotNull();

  // Range operators
  List<Person> findByAgeBetween(int from, int to);

  // Negation (Not)
  List<Person> findByAgeNot(int age);

  // OrderBy (method-name sort)
  List<Person> findByAgeGreaterThanOrderByNameAsc(int age);

  List<Person> findByAgeGreaterThanOrderByNameDesc(int age);

  List<Person> findByAgeGreaterThanOrderByAgeDesc(int age);

  // OrderBy with Pageable (for testing sort precedence)
  List<Person> findByAgeGreaterThanOrderByNameAsc(int age, Pageable pageable);

  // Delete by query
  int deleteByName(String name);

  int deleteByAgeLessThan(int age);

  // Count methods
  int countByAgeGreaterThan(int age);

  // Multi-criteria AND queries
  List<Person> findByNameAndAge(String name, int age);

  // Update by query
  void updatePerson(@Id String id, @Parameter("name") String name);

  long updateByName(String name, @Parameter("age") int age);
}
